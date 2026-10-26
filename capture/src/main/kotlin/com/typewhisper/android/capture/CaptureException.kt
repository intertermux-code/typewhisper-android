package com.typewhisper.android.capture

/**
 * Failures while opening or reading the microphone.
 */
public class CaptureException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
