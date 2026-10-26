package com.typewhisper.android.capture

import android.media.AudioDeviceInfo
import android.os.SystemClock
import com.typewhisper.android.engineapi.AudioData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Records microphone audio and produces [AudioData] honoring the engine
 * contract: 16 kHz mono Float32 normalized to [-1, 1].
 *
 * Flow: [MicAudioSource] captures at the negotiated device rate into a
 * growable buffer; on stop the buffer is resampled to 16 kHz (a no-op copy
 * when the device natively captured at 16 kHz).
 *
 * One instance records one utterance: [stop] and [cancel] are requests against the
 * in-flight [record] call and there is no second recording to be had from it. That is
 * deliberate — a fresh instance cannot have a stale stop flag, so a stop that arrives
 * while [record] is still opening the microphone is honoured instead of being reset away.
 *
 * @param maxDurationMs hard stop for a recording; [stop] ends it earlier.
 * @param preferredDevice the mic chosen in settings, applied only by the default
 *   [audioSourceFactory].
 * @param audioSourceFactory injectable for tests.
 */
public class RecordingSession(
    private val maxDurationMs: Long = 10_000L,
    private val preferredDevice: AudioDeviceInfo? = null,
    private val audioSourceFactory: () -> MicAudioSource = { MicAudioSource(preferredDevice = preferredDevice) },
) {
    private val stopRequested = AtomicBoolean(false)
    private val discardRequested = AtomicBoolean(false)

    /** Requests an early end of the in-flight [record] call. Thread-safe. */
    public fun stop() {
        stopRequested.set(true)
    }

    /**
     * Ends the in-flight [record] call and marks its audio as unwanted.
     *
     * [discardRequested] is set before [stopRequested] so a capture loop that observes the
     * stop is guaranteed to observe the discard too: the flag order decides whether an
     * accidental tap is transcribed, and it should never be.
     */
    public fun cancel() {
        discardRequested.set(true)
        stopRequested.set(true)
    }

    /** Whether [cancel] ended this recording, so callers know to drop the audio. */
    public fun isDiscarded(): Boolean = discardRequested.get()

    /**
     * Records until [stop] or [cancel] is called, or [maxDurationMs] elapses.
     *
     * Runs the blocking read loop on [Dispatchers.IO]. [onAmplitude] receives
     * the per-chunk peak amplitude in [0, 1] for level meters; it is invoked
     * on the IO thread, so UI observers must hop to Main.
     *
     * @throws CaptureException when the microphone cannot be opened or read.
     */
    @Throws(CaptureException::class)
    public suspend fun record(onAmplitude: (Float) -> Unit = {}): AudioData =
        withContext(Dispatchers.IO) {
            if (stopRequested.get()) {
                // Already ended: the release landed while the microphone was still being
                // opened. Hand back silence rather than opening a mic nobody wants.
                return@withContext AudioData(
                    samples = FloatArray(0),
                    sampleRateHz = MicAudioSource.TARGET_SAMPLE_RATE_HZ,
                    durationMs = 0L,
                )
            }
            val source = audioSourceFactory()
            try {
                source.start()
                val sink = GrowableFloatArray()
                val chunk = ShortArray(READ_CHUNK_SAMPLES)
                val startNs = SystemClock.elapsedRealtimeNanos()
                while (true) {
                    // Checked before the read, not after it: the read is the only call in
                    // this loop that can wait, so checking it afterwards would add a whole
                    // chunk of latency to every stop request.
                    if (stopRequested.get()) break
                    val read = source.read(chunk, 0, chunk.size)
                    if (read < 0) throw CaptureException("AudioRecord read failed: $read")
                    if (read > 0) {
                        var peak = 0f
                        for (i in 0 until read) {
                            val sample = chunk[i] / 32_768f
                            sink.add(sample)
                            val magnitude = abs(sample)
                            if (magnitude > peak) peak = magnitude
                        }
                        onAmplitude(peak.coerceIn(0f, 1f))
                    }
                    if (elapsedMs(startNs) >= maxDurationMs) break
                }
                val samples = sink.toArray()
                return@withContext AudioData(
                    samples = LinearResampler.resample(
                        samples,
                        source.sampleRateHz,
                        MicAudioSource.TARGET_SAMPLE_RATE_HZ,
                    ),
                    sampleRateHz = MicAudioSource.TARGET_SAMPLE_RATE_HZ,
                    durationMs = elapsedMs(startNs),
                )
            } finally {
                source.release()
            }
        }

    private fun elapsedMs(startNs: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L

    private companion object {
        /**
         * Shorts per blocking read. Small enough that a stop request is noticed within
         * ~64 ms of 16 kHz audio, large enough that the read itself is not the bottleneck.
         */
        const val READ_CHUNK_SAMPLES = 1024
    }

    /** Minimal doubling float buffer; avoids boxing 480k samples per session. */
    private class GrowableFloatArray(initialCapacity: Int = 16_384) {
        private var data = FloatArray(initialCapacity)
        private var size = 0

        fun add(value: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }
}
