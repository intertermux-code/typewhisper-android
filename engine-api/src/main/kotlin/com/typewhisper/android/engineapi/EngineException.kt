package com.typewhisper.android.engineapi

import java.io.IOException

/**
 * Typed failures from [EnginePlugin.transcribe]. Hosts should map these to
 * user-facing messages; [message] never contains credentials.
 */
public sealed class EngineException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Missing or rejected credential (HTTP 401/403). */
    public class Authentication(message: String) : EngineException(message)

    /** Rate limited (HTTP 429). [retryAfterMs] from `Retry-After`, if present. */
    public class RateLimited(
        message: String,
        public val retryAfterMs: Long? = null,
    ) : EngineException(message)

    /** The request was rejected (HTTP 400/422), e.g. bad audio or model name. */
    public class BadRequest(message: String) : EngineException(message)

    /** Engine-side failure (HTTP 5xx). */
    public class ServerError(
        public val statusCode: Int,
        message: String,
    ) : EngineException(message)

    /**
     * Transport failure: timeouts, DNS, TLS, connection resets. Hosts that can name the
     * thing the user should check (a Mac that is asleep, a relay that is not running)
     * pass [message]; otherwise the underlying IO message is used.
     */
    public class Network(cause: IOException, message: String? = null) :
        EngineException(message ?: "Network error: ${cause.message}", cause)

    /** The engine answered, but the payload could not be understood. */
    public class InvalidResponse(
        message: String,
        cause: Throwable? = null,
    ) : EngineException(message, cause)
}
