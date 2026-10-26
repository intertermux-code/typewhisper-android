package com.typewhisper.android.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.typewhisper.android.capture.LinearResampler
import com.typewhisper.android.engineapi.AudioData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns an audio file picked from storage into the engine contract: mono float PCM at
 * 16 kHz. The framework's own decoders cover mp3/aac/flac/ogg/vorbis/wav, so no native
 * dependency is added for file transcription.
 *
 * Everything user-facing goes out as [Unsupported] with a sentence a human can act on —
 * a file that cannot be decoded is a fact about the file, not a stack trace.
 */
object AudioFileDecoder {

    class Unsupported(message: String) : Exception(message)

    private const val TARGET_RATE_HZ = 16_000
    private const val MAX_SECONDS = 3_600L

    suspend fun decode(context: Context, uri: Uri): AudioData = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            runCatching { extractor.setDataSource(context, uri, null) }
                .getOrElse { throw Unsupported("That file could not be opened as audio.") }

            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw Unsupported("That file has no audio track.")

            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw Unsupported("That audio format is unnamed.")
            if (durationSeconds(format) > MAX_SECONDS) {
                throw Unsupported("Only files up to an hour long can be transcribed.")
            }
            val sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
                ?: throw Unsupported("That file does not say its sample rate.")
            val channels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
            val pcmEncoding = format.intOrNull(MediaFormat.KEY_PCM_ENCODING)
                ?: AudioFormat.ENCODING_PCM_16BIT

            extractor.selectTrack(track)
            val decoder = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: Exception) {
                throw Unsupported("$mime is not supported by this phone.")
            }
            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                val samples = drain(decoder, extractor, pcmEncoding, channels)
                if (samples.isEmpty()) throw Unsupported("That file decoded to silence.")
                val mono = LinearResampler.resample(samples, sampleRate, TARGET_RATE_HZ)
                AudioData(
                    samples = mono,
                    sampleRateHz = TARGET_RATE_HZ,
                    durationMs = mono.size * 1000L / TARGET_RATE_HZ,
                )
            } finally {
                runCatching { decoder.stop() }
                runCatching { decoder.release() }
            }
        } finally {
            extractor.release()
        }
    }

    /** The sync decode loop: feed samples in, collect downmixed floats out. */
    private fun drain(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        pcmEncoding: Int,
        channels: Int,
    ): FloatArray {
        val info = MediaCodec.BufferInfo()
        val out = ArrayList<FloatArray>()
        var outSize = 0
        var inputDone = false
        var timedOut = 0

        while (true) {
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)
                        ?: throw Unsupported("The decoder lost its input buffer.")
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = decoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            when {
                outIndex >= 0 -> {
                    val buffer = decoder.getOutputBuffer(outIndex)
                    if (buffer != null && info.size > 0) {
                        val chunk = decodeChunk(buffer, info.size, pcmEncoding, channels)
                        out += chunk
                        outSize += chunk.size
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
                // A decoder stuck between states is looped out by a timeout budget rather
                // than a stuck loop; a truncated file beats an unresponsive UI.
                ++timedOut > MAX_TIMEOUTS -> throw Unsupported("That file stopped decoding partway.")
                else -> { /* INFO_TRY_AGAIN/FORMAT_CHANGED etc.: keep looping */ }
            }
            if (outIndex >= 0) timedOut = 0
        }

        val flat = FloatArray(outSize)
        var offset = 0
        for (chunk in out) {
            chunk.copyInto(flat, offset)
            offset += chunk.size
        }
        return flat
    }

    private fun decodeChunk(
        buffer: java.nio.ByteBuffer,
        size: Int,
        pcmEncoding: Int,
        channels: Int,
    ): FloatArray {
        buffer.position(0)
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val frames = size / 4 / channels
                FloatArray(frames) { frame ->
                    var sum = 0f
                    for (channel in 0 until channels) {
                        sum += buffer.getFloat((frame * channels + channel) * 4)
                    }
                    sum / channels
                }
            }
            AudioFormat.ENCODING_PCM_16BIT -> {
                val frames = size / 2 / channels
                FloatArray(frames) { frame ->
                    var sum = 0
                    for (channel in 0 until channels) {
                        sum += buffer.getShort((frame * channels + channel) * 2).toInt()
                    }
                    (sum / channels) / 32768f
                }
            }
            else -> throw Unsupported("Only 16-bit and 32-bit float PCM can be decoded from $pcmEncoding audio.")
        }
    }

    private fun durationSeconds(format: MediaFormat): Long =
        runCatching { format.getLong(MediaFormat.KEY_DURATION) / 1_000_000L }.getOrDefault(0L)

    private fun MediaFormat.intOrNull(key: String): Int? =
        runCatching { getInteger(key) }.getOrNull()

    private const val DEQUEUE_TIMEOUT_US = 10_000L
    private const val MAX_TIMEOUTS = 200
}
