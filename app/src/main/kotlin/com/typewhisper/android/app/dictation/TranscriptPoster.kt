package com.typewhisper.android.app.dictation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService

/**
 * Delivers dictated text to the user. Kept as one small class so the M5 IME
 * path can grow a sibling [commit] branch beside [deliver] without touching
 * the service that calls it.
 *
 * Insertion here is clipboard-only: Android offers no other Play-safe global
 * injection without an IME, and by policy no AccessibilityService is used.
 * POST_NOTIFICATIONS gates whether the nudge is actually visible on API 33+,
 * but the copy always succeeds regardless — a tap-to-open notice that never
 * rendered would be worse than a silent clipboard write, so the failure to
 * post is swallowed rather than surfaced.
 */
class TranscriptPoster(private val context: Context) {

    fun deliver(text: String, note: String? = null, offerTranslate: Boolean = false) {
        copy(text)
        runCatching {
            NotificationManagerCompat.from(context).notify(
                Notifications.ID_RESULT,
                Notifications.pasteNudge(context, text, note, offerTranslate),
            )
        }
    }

    /** A translation replaces the transcript it came from; the nudge says which language. */
    fun deliverTranslation(text: String, targetLabel: String) {
        copy(text)
        runCatching {
            NotificationManagerCompat.from(context).notify(
                Notifications.ID_RESULT,
                Notifications.translation(context, text, targetLabel),
            )
        }
    }

    /** The clipboard half of [deliver], for callers that only want the copy. */
    fun copy(text: String) {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
    }

    private companion object {
        const val CLIP_LABEL = "TypeWhisper"
    }
}
