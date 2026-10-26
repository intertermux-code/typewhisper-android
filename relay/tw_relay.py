#!/usr/bin/env python3
"""TCP forwarder exposing TypeWhisper's loopback HTTP server to the network.

TypeWhisper binds its API to 127.0.0.1 only, so the phone can't reach it
directly. This relay listens on a fixed port, finds TypeWhisper's current
port via its discovery file, and pipes raw TCP both ways — no HTTP parsing,
so the phone's Authorization: Bearer header passes through untouched.

Usage:
    python3 tw_relay.py [--host HOST] [--port PORT] [--default-port N]
                        [--discovery-file PATH]

Environment overrides (CLI flags win): TW_RELAY_HOST, TW_RELAY_PORT,
TW_RELAY_DEFAULT_PORT, TW_RELAY_DISCOVERY_FILE.
"""

import argparse
import json
import logging
import os
import select
import signal
import socket
import socketserver
import sys
import threading
from pathlib import Path

TW_SUPPORT_DIR = Path.home() / "Library" / "Application Support" / "TypeWhisper"
DEFAULT_DISCOVERY = TW_SUPPORT_DIR / "api-discovery.json"
DEFAULT_PORT_FILE = TW_SUPPORT_DIR / "api-port"
DEFAULT_UPSTREAM_PORT = 8978
# Loopback by default: expose the relay on an address the phone can actually reach
# by passing --host (or TW_RELAY_HOST) — see README.md.
DEFAULT_HOST = "127.0.0.1"
DEFAULT_LISTEN_PORT = 18978
LOG_FILE = Path(__file__).resolve().parent / "tw_relay.log"

# Keep short enough that a dead upstream still yields a quick 502.
UPSTREAM_CONNECT_TIMEOUT = 3.0
PIPE_BUFFER = 65536

log = logging.getLogger("tw_relay")


def _find_port_in(obj):
    """Pull an integer 'port' out of a discovery JSON of unknown shape.

    TypeWhisper hasn't shipped a discovery file on this Mac yet, so accept
    {"port": N} as well as nesting under "http"/"server"/"api" sections.
    """
    if isinstance(obj, dict):
        for key in ("port", "httpPort", "apiPort"):
            value = obj.get(key)
            if isinstance(value, bool):
                continue
            if isinstance(value, int) and 1 <= value <= 65535:
                return value
        for section in ("http", "server", "api"):
            if section in obj:
                found = _find_port_in(obj[section])
                if found:
                    return found
    return None


def discover_port(config):
    """Return TypeWhisper's live upstream port, never raising.

    discovery JSON -> api-port text file -> configured default.
    """
    try:
        with open(config["discovery_file"], "r", encoding="utf-8") as fh:
            port = _find_port_in(json.load(fh))
        if port:
            return port
        log.warning("discovery file %s has no usable port field",
                    config["discovery_file"])
    except FileNotFoundError:
        pass
    except (OSError, ValueError) as exc:
        log.warning("could not read discovery file: %s", exc)

    try:
        with open(config["port_file"], "r", encoding="utf-8") as fh:
            port = int(fh.read().strip())
        if 1 <= port <= 65535:
            return port
        log.warning("api-port file holds %r, out of range", port)
    except FileNotFoundError:
        pass
    except (OSError, ValueError) as exc:
        log.warning("could not read api-port file: %s", exc)

    return config["default_port"]


def connect_upstream(config):
    """Open a socket to TypeWhisper, or return None.

    On refusal, re-read the discovery file before giving up: TypeWhisper
    picks a fresh port on every restart, so a stale value there is the most
    likely cause.
    """
    port = discover_port(config)
    tried = set()
    while port not in tried:
        tried.add(port)
        try:
            sock = socket.create_connection(
                ("127.0.0.1", port), timeout=UPSTREAM_CONNECT_TIMEOUT)
            log.info("upstream connected on port %d", port)
            return sock
        except OSError as exc:
            log.warning("upstream port %d refused: %s", port, exc)
        port = discover_port(config)
    return None


def reply_502(client):
    body = (b"TypeWhisper HTTP server not reachable. Enable it in "
            b"TypeWhisper settings (HTTP server toggle), then retry.\r\n")
    try:
        client.sendall(b"HTTP/1.1 502 Bad Gateway\r\n"
                       b"Content-Type: text/plain\r\n"
                       b"Connection: close\r\n"
                       b"Content-Length: " + str(len(body)).encode() + b"\r\n"
                       b"\r\n" + body)
    except OSError:
        pass


def pump(client, upstream):
    """Relay bytes both ways until either side closes."""
    sockets = (client, upstream)
    try:
        while True:
            readable, _, expired = select.select(sockets, [], sockets, None)
            if not readable or expired:
                return
            for source in readable:
                peer = upstream if source is client else client
                try:
                    data = source.recv(PIPE_BUFFER)
                except OSError:
                    return
                if not data:
                    return
                peer.sendall(data)
    except OSError as exc:
        log.info("pipe ended: %s", exc)


class RelayHandler(socketserver.BaseRequestHandler):
    def handle(self):
        config = self.server.relay_config
        client = self.request
        client.settimeout(None)
        upstream = connect_upstream(config)
        if upstream is None:
            # Keep the client alive just long enough to read the 502 instead
            # of staring at a hung connection.
            peer = client.getpeername()[:2]
            log.error("no upstream reachable for request from %s", peer)
            reply_502(client)
            return
        log.info("proxying %s -> 127.0.0.1:%d",
                 client.getpeername()[:2], upstream.getpeername()[1])
        try:
            pump(client, upstream)
        finally:
            upstream.close()


class RelayServer(socketserver.ThreadingTCPServer):
    daemon_threads = True
    allow_reuse_address = True


def build_config(args):
    return {
        "discovery_file": Path(os.path.expanduser(args.discovery_file)),
        "port_file": Path(os.path.expanduser(args.port_file)),
        "default_port": args.default_port,
    }


def setup_logging():
    log.setLevel(logging.INFO)
    fmt = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    stream = logging.StreamHandler(sys.stdout)
    stream.setFormatter(fmt)
    handlers = [stream]
    try:
        file_handler = logging.FileHandler(LOG_FILE, encoding="utf-8")
        file_handler.setFormatter(fmt)
        handlers.append(file_handler)
    except OSError as exc:
        print("file logging unavailable: %s" % exc, file=sys.stderr)
    for handler in handlers:
        log.addHandler(handler)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--host", default=os.environ.get("TW_RELAY_HOST", DEFAULT_HOST))
    parser.add_argument("--port", type=int,
                        default=int(os.environ.get("TW_RELAY_PORT", DEFAULT_LISTEN_PORT)))
    parser.add_argument("--default-port", type=int,
                        default=int(os.environ.get("TW_RELAY_DEFAULT_PORT", DEFAULT_UPSTREAM_PORT)),
                        help="upstream port to use when discovery files are absent")
    parser.add_argument("--discovery-file",
                        default=os.environ.get("TW_RELAY_DISCOVERY_FILE", str(DEFAULT_DISCOVERY)))
    parser.add_argument("--port-file",
                        default=os.environ.get("TW_RELAY_PORT_FILE", str(DEFAULT_PORT_FILE)))
    args = parser.parse_args()

    setup_logging()
    log.info("relay starting on %s:%d (discovery: %s)",
             args.host, args.port, args.discovery_file)

    server = RelayServer((args.host, args.port), RelayHandler)
    server.relay_config = build_config(args)

    def shutdown(signum, frame):
        log.info("signal %d received, shutting down", signum)
        threading.Thread(target=server.shutdown, daemon=True).start()

    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)

    try:
        server.serve_forever()
    finally:
        server.server_close()
        log.info("relay stopped")


if __name__ == "__main__":
    main()
