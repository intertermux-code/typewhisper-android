package com.typewhisper.android.engines.whispercpp

import com.typewhisper.android.engineapi.TranscriptSegment

internal data class ParsedTranscription(
    val aborted: Boolean,
    val detectedLanguage: String?,
    val text: String,
    val segments: List<TranscriptSegment>,
)

/**
 * Decodes the line-oriented payload returned by the JNI bridge.
 *
 * Kept as a pure function because the wire format is the only place the native
 * and Kotlin layers agree on structure — parsing it on the JVM lets the
 * contract be tested without loading the .so.
 *
 * Layout produced by whisper_jni.cpp:
 *   lang=<name>          (~prefix marks an aborted run)
 *   <segmentCount>
 *   <startMs>\t<endMs>\t<text>   (repeated; text is already newline-sanitized)
 */
internal object TranscriptCodec {

    fun parse(raw: String): ParsedTranscription {
        if (raw.isEmpty()) {
            return ParsedTranscription(false, null, "", emptyList())
        }

        val lines = raw.split('\n')
        // Trailing separator from the native side yields one empty final line.
        val header = lines.getOrNull(0).orEmpty()
        require(header.startsWith("lang=")) { "malformed transcript header" }

        val langToken = header.removePrefix("lang=")
        val aborted = langToken.startsWith("~")
        val langName = langToken.removePrefix("~")
        val detectedLanguage = langName.takeUnless { it.isEmpty() || it == "?" }

        val count = lines.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        val segments = ArrayList<TranscriptSegment>(if (count > 0) count else 0)
        val builder = StringBuilder()

        for (i in 0 until count) {
            val line = lines.getOrNull(2 + i) ?: break
            val first = line.indexOf('\t')
            if (first < 0) continue
            val second = line.indexOf('\t', first + 1)
            if (second < 0) continue
            val startMs = line.substring(0, first).toLongOrNull() ?: continue
            val endMs = line.substring(first + 1, second).toLongOrNull() ?: continue
            val text = line.substring(second + 1)
            if (text.isNotEmpty()) {
                segments += TranscriptSegment(text = text, startMs = startMs, endMs = endMs)
                builder.append(text)
            }
        }

        return ParsedTranscription(
            aborted = aborted,
            detectedLanguage = detectedLanguage,
            text = builder.toString().trim(),
            segments = segments,
        )
    }
}
