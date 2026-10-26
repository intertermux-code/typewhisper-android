package com.typewhisper.android.capture

/** What the caller should do with the audio of a press that has just ended. */
public enum class ReleaseAction {
    /** The press never became a dictation: leave everything as it was. */
    None,

    /** Stops capture and throws the samples away — no transcription, no result. */
    Discard,

    /** Stops capture and transcribes what was recorded. */
    StopAndTranscribe,
}

/**
 * The decision table behind one push-to-talk press: press and hold to record, release to
 * stop and transcribe.
 *
 * Deliberately free of Android types so the bubble's `MotionEvent`s and the keyboard's
 * `MotionEvent`s can both be reduced to it — and so the rules are unit-testable:
 *
 *  - a press shorter than [minHoldMs] is an accident, so its audio is discarded instead of
 *    turned into an empty transcription;
 *  - a press that slid far enough to be a drag of the bubble is repositioning, not talking,
 *    so it is discarded too;
 *  - [release] is idempotent, which is what keeps a stray `ACTION_UP` or an `ACTION_CANCEL`
 *    from ever leaving the microphone hot.
 *
 * One instance describes one press; callers create it on `ACTION_DOWN` and drop it on
 * `ACTION_UP`.
 *
 * @param minHoldMs how long the finger has to stay down for the press to count.
 */
public class PushToTalkPress(public val minHoldMs: Long = MIN_HOLD_MS) {

    /** True between `ACTION_DOWN` and the matching `ACTION_UP`/`ACTION_CANCEL`. */
    public var isActive: Boolean = false
        private set

    /** Set once the finger has slid far enough that the press is a drag. */
    public var isDragging: Boolean = false
        private set

    /** Records the start of a press. Always call on `ACTION_DOWN`. */
    public fun down() {
        isActive = true
        isDragging = false
    }

    /** Marks the press as a drag, which can never become a dictation. */
    public fun drag() {
        isDragging = true
    }

    /**
     * Ends the press, given how long the finger was down. Safe to call from both
     * `ACTION_UP` and `ACTION_CANCEL`, and safe to call twice.
     */
    public fun release(elapsedMs: Long): ReleaseAction {
        if (!isActive) return ReleaseAction.None
        isActive = false
        return when {
            isDragging || elapsedMs < minHoldMs -> ReleaseAction.Discard
            else -> ReleaseAction.StopAndTranscribe
        }
    }

    public companion object {
        /** Long enough to be deliberate, short enough not to feel laggy. */
        public const val MIN_HOLD_MS: Long = 300L
    }
}
