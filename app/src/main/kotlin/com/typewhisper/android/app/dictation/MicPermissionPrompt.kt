package com.typewhisper.android.app.dictation

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Surfaces a denied microphone permission as a notification instead of a crash.
 *
 * Triggers (tile / bubble) fire outside any activity, so there is no UI to
 * inline the request into; a tappable notice that opens the app — where the
 * runtime prompt lives — is the honest, non-crashing response.
 */
internal object MicPermissionPrompt {

    fun notify(context: Context) {
        Notifications.ensureChannels(context)
        // runCatching: without POST_NOTIFICATIONS the post throws on API 33+, and a
        // permission prompt that crashes would defeat its own purpose.
        runCatching {
            NotificationManagerCompat.from(context).notify(
                Notifications.ID_ALERT,
                Notifications.failure(
                    context,
                    "Microphone access is off. Open TypeWhisper to grant it.",
                ),
            )
        }
    }
}
