package com.typewhisper.android.engines.whispercpp

/**
 * Thin Kotlin surface over the JNI entry points in whisper_jni.cpp.
 *
 * Isolated as its own object so the native library is only loaded when a
 * transcription is actually attempted — the pure model/language/parsing helpers
 * remain testable on a JVM with no .so present.
 */
internal object WhisperNative {

    init {
        System.loadLibrary("whisper_jni")
    }

    /** Returns a whisper context pointer, or 0 when the model could not load. */
    external fun nativeInit(modelPath: String): Long

    external fun nativeRelease(ctxPtr: Long)

    /**
     * Runs whisper_full over [samples]. [language] is a whisper language name
     * ("en", "auto", ...). [cancelHandle] comes from [nativeCancelNew]; passing
     * 0 disables cancellation. Returns the serialized transcript string.
     */
    external fun nativeTranscribe(
        ctxPtr: Long,
        samples: FloatArray,
        nThreads: Int,
        language: String,
        cancelHandle: Long,
    ): String

    external fun nativeCancelNew(): Long
    external fun nativeCancelRequest(handle: Long)
    external fun nativeCancelFree(handle: Long)

    external fun nativeSampleRate(): Int
}
