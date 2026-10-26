package com.typewhisper.android.engines.whispercpp

/**
 * Catalog of the on-device models the engine can download.
 *
 * The repo brief asked for GGUF `tiny`/`base` `q5_0`. The vendored whisper.cpp
 * v1.7.4 predates GGUF loading and reads only the classic `ggml-*.bin` format,
 * and in that format the small quantized checkpoints are published as `q5_1`
 * (there is no tiny/base q5_0). These URLs are the loadable equivalents.
 */
enum class WhisperModel(
    /** Stable id stored in settings. */
    val id: String,
    /** File name written into the app's files dir. */
    val fileName: String,
    /** Absolute download URL, kept literal so it can be audited. */
    val url: String,
    /** Approximate size on disk, used for the pre-download storage check. */
    val sizeBytes: Long,
) {
    TINY_Q5_1(
        id = "tiny-q5_1",
        fileName = "ggml-tiny-q5_1.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin",
        sizeBytes = 32_152_673L,
    ),
    BASE_Q5_1(
        id = "base-q5_1",
        fileName = "ggml-base-q5_1.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
        sizeBytes = 59_707_625L,
    ),
    ;

    companion object {
        val DEFAULT: WhisperModel = TINY_Q5_1

        fun fromId(id: String?): WhisperModel =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
