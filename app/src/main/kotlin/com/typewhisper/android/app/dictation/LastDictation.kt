package com.typewhisper.android.app.dictation

import com.typewhisper.android.engineapi.AudioData

/**
 * The samples of the most recent successful dictation, held so the paste nudge can
 * offer "Translate" without asking the user to speak again. The Mac re-runs its engine
 * on the same audio with a target language; nothing else needs the samples to outlive
 * the loop, so they are kept exactly as long as that offer can plausibly be taken.
 *
 * In-memory only: dictated audio never touches the disk.
 */
object LastDictation {

    class Held(val audio: AudioData, val text: String, val atMs: Long)

    private const val FRESH_MS = 5 * 60 * 1000L

    @Volatile
    private var held: Held? = null

    fun hold(audio: AudioData, text: String) {
        held = Held(audio, text, System.currentTimeMillis())
    }

    /** The held dictation, or null once it is too old for a second pass to make sense. */
    fun fresh(): Held? = held?.takeIf { System.currentTimeMillis() - it.atMs <= FRESH_MS }
}
