# TypeWhisper Mac relay

A small Python TCP forwarder so the phone can reach TypeWhisper's API, which
only listens on the Mac's 127.0.0.1. The relay takes connections on an address
the phone can see and pipes the raw bytes to TypeWhisper's current port; the
phone's Bearer token passes through untouched — the relay does no HTTP and
no auth of its own.

Runs on the Mac only. Not part of the APK.

## Files

- `tw_relay.py` — the forwarder. Standard library only, needs Python 3.9+.
- `com.typewhisper.twrelay.plist.template` — LaunchAgent definition, with
  `__HOME__` placeholders to fill in before install.
- `tw_relay.log` — relay's own timestamped log (append, created at runtime).
- `launchd.out.log` / `launchd.err.log` — launchd's capture of stdout/stderr.

## How it finds TypeWhisper

Per connection, in order:

1. `~/Library/Application Support/TypeWhisper/api-discovery.json` — the port
   is taken from a `port`/`httpPort`/`apiPort` key at the top level or nested
   under `http`/`server`/`api`.
2. `~/Library/Application Support/TypeWhisper/api-port` — a plain number.
3. The fallback port (`--default-port`, default 8978).

If a connection to the upstream is refused, the discovery file is re-read
before giving up — TypeWhisper picks a new port on every restart, so the
relay picks up port changes without being restarted. If nothing answers, the
client gets a plain `502 Bad Gateway` telling them to enable the HTTP server.

## Options

| Flag                | Env var                 | Default                            |
|---------------------|-------------------------|------------------------------------|
| `--host`            | `TW_RELAY_HOST`         | `127.0.0.1` (loopback — see below) |
| `--port`            | `TW_RELAY_PORT`         | `18978`                            |
| `--default-port`    | `TW_RELAY_DEFAULT_PORT` | `8978`                             |
| `--discovery-file`  | `TW_RELAY_DISCOVERY_FILE` | TypeWhisper's `api-discovery.json` |
| `--port-file`       | `TW_RELAY_PORT_FILE`    | TypeWhisper's `api-port`           |

`--host` defaults to loopback so the relay is never exposed by accident. To
reach it from a phone, pass the address of an interface the phone can see:
your tailnet address (`tailscale ip -4` on the Mac), a LAN address, or a
Tailscale Serve/Funnel fronting `127.0.0.1:18978`. Whatever you pick is what
goes into the app's Relay host field.

## Running it manually

```sh
python3 relay/tw_relay.py --host "$(tailscale ip -4)"
```

Or from an installed copy, `~/tw-android/relay/tw_relay.py`. Stop with Ctrl-C
(or `kill`; SIGTERM shuts down cleanly).

## Installed as a LaunchAgent

The template has to be filled in first, because launchd does no variable
expansion. From the repo root:

```sh
mkdir -p ~/Library/LaunchAgents
sed "s|__HOME__|$HOME|g" relay/com.typewhisper.twrelay.plist.template \
  > ~/Library/LaunchAgents/com.typewhisper.twrelay.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.typewhisper.twrelay.plist
```

If the repo does not live at `$HOME/tw-android`, edit the paths in the
template (or run `sed` on the two strings) before bootstrapping.

A launchd `ProgramArguments` entry that points into a volume mounted
`noowners`, or a plist loaded from such a volume, is rejected with
"bad ownership or modes" (bootstrap error 5). If that is your setup, install
the filled-in plist to `/Users/Shared/LaunchAgents/` and bootstrap from there
instead — the substitution command above only changes where the copy lives.

Check:

```sh
launchctl list | grep twrelay
launchctl print gui/$(id -u)/com.typewhisper.twrelay | head
```

Restart after editing `tw_relay.py`:

```sh
launchctl kickstart -k gui/$(id -u)/com.typewhisper.twrelay
```

Uninstall:

```sh
launchctl bootout gui/$(id -u)/com.typewhisper.twrelay
rm ~/Library/LaunchAgents/com.typewhisper.twrelay.plist
```

If you keep a login-time script that bootstraps your agents, add the
`launchctl bootstrap` line there so the relay comes back after a reboot;
otherwise re-run it by hand.

## Manual test once TypeWhisper's HTTP server is enabled

1. TypeWhisper settings → enable the HTTP server (it binds 127.0.0.1 only —
   that's why this relay exists).
2. Confirm discovery appeared:
   `cat "$HOME/Library/Application Support/TypeWhisper/api-discovery.json"`
3. From this Mac, through the relay:
   `curl -s http://127.0.0.1:18978/v1/status`
   Expect a real TypeWhisper JSON reply, not the relay's 502 text.
4. From the phone (on the same tailnet / network; check with `tailscale
   status` — the phone appears there when connected):
   `curl -s -H "Authorization: Bearer <your token>" http://<relay-host>:18978/v1/status`
5. Quit and relaunch TypeWhisper — if its port changed, the very next
   request through the relay should still work (the relay re-reads the
   discovery file on connection failure).

The forwarding, port re-discovery, 502 path, and header passthrough can each
be proven against a throwaway `python3 -m http.server` upstream, but that is
not a substitute for steps 3–5 above.
