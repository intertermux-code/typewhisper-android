package com.typewhisper.android.capture

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Microphone access via [AudioRecord].
 *
 * Requests [TARGET_SAMPLE_RATE_HZ] with the [MediaRecorder.AudioSource.VOICE_RECOGNITION]
 * source (tuned for speech: disables most vendor post-processing). When the
 * device rejects 16 kHz, the nearest usable rate is negotiated instead and
 * [RecordingSession] resamples back to 16 kHz, so callers always observe the
 * [com.typewhisper.android.engineapi.AudioData] contract.
 *
 * Not thread-safe: call [start], [read], [release] from a single thread
 * (see [RecordingSession]).
 *
 * @param audioSource the [MediaRecorder.AudioSource] to capture from.
 * @param preferredDevice the mic the user picked in settings, or null to let the
 *   platform route. It is a hint: the HAL may keep using another device, which is
 *   why the return value of `setPreferredDevice` is not treated as an error.
 */
public class MicAudioSource(
    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    private val preferredDevice: AudioDeviceInfo? = null,
) {
    public companion object {
        /** Pipeline target: 16 kHz mono, matching the engine contract. */
        public const val TARGET_SAMPLE_RATE_HZ: Int = 16_000

        private val FALLBACK_RATES = intArrayOf(44_100, 48_000, 22_050, 32_000, 11_025, 8_000)
    }

    private var recorder: AudioRecord? = null
    private var negotiatedRateHz: Int = TARGET_SAMPLE_RATE_HZ

    /** The rate the recorder was actually opened at. Valid after [start]. */
    public val sampleRateHz: Int get() = negotiatedRateHz

    /**
     * Opens the recorder and begins capturing. Throws [CaptureException] when
     * no usable configuration exists (e.g. permission denied at the HAL level).
     *
     * Callers must hold `RECORD_AUDIO` before invoking this.
     */
    @Throws(CaptureException::class)
    public fun start() {
        release()
        val rateHz = negotiateRateHz()
        val minBufferBytes = AudioRecord.getMinBufferSize(
            rateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferBytes <= 0) {
            throw CaptureException("No usable AudioRecord buffer at ${rateHz}Hz")
        }
        val recorder = AudioRecord.Builder()
            .setAudioSource(audioSource)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rateHz)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            // ~1 s of headroom over the minimum; large enough to survive GC pauses.
            .setBufferSizeInBytes(maxOf(minBufferBytes * 4, rateHz * 2))
            .build()
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw CaptureException("AudioRecord failed to initialize at ${rateHz}Hz")
        }
        if (preferredDevice != null) {
            // Ignored return value: a refused hint still records, just on another device.
            recorder.setPreferredDevice(preferredDevice)
        }
        recorder.startRecording()
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            recorder.release()
            throw CaptureException("AudioRecord failed to start recording at ${rateHz}Hz")
        }
        this.recorder = recorder
        negotiatedRateHz = rateHz
    }

    /**
     * Blocking read of PCM16 mono samples into [buffer].
     *
     * @return number of shorts written, or a negative AudioRecord error code.
     */
    public fun read(buffer: ShortArray, offset: Int, length: Int): Int {
        val recorder = recorder ?: return AudioRecord.ERROR_INVALID_OPERATION
        return recorder.read(buffer, offset, length, AudioRecord.READ_BLOCKING)
    }

    /** Stops capturing and frees the recorder. Safe to call when not started. */
    public fun release() {
        recorder?.release()
        recorder = null
    }

    private fun negotiateRateHz(): Int {
        for (rateHz in intArrayOf(TARGET_SAMPLE_RATE_HZ) + FALLBACK_RATES) {
            val minBufferBytes = AudioRecord.getMinBufferSize(
                rateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBufferBytes > 0) return rateHz
        }
        throw CaptureException("Device reports no usable microphone sample rate")
    }
}
