package com.typewhisper.android.app.dictation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.typewhisper.android.app.MainActivity
import com.typewhisper.android.app.R
import com.typewhisper.android.ime.ImeSetup

/**
 * Notification channels, ids, and the shared builders for the dictation loop.
 *
 * Three channels because the three notification kinds have different
 * importance and interruption budgets: the ongoing recording notice is silent
 * and permanent, the paste nudge should buzz once, and failures must be heard.
 */
internal object Notifications {

    const val CHANNEL_ONGOING = "tw_recording"
    const val CHANNEL_RESULT = "tw_result"
    const val CHANNEL_ALERT = "tw_alert"

    const val ID_ONGOING = 1001
    const val ID_RESULT = 1002
    const val ID_ALERT = 1003

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                "Dictation in progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shows while TypeWhisper is recording." },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULT,
                "Transcription ready",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Appears when dictated text is ready to paste." },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                "Problems",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Microphone and transcription failures." },
        )
    }

    /**
     * The notice that owns the foreground notification while a dictation is running.
     *
     * Its text follows [phase] because the wait after the microphone goes quiet is the part
     * users mistake for a dead button: "Stopping…" is the notification saying the tap landed.
     */
    fun ongoing(context: Context, phase: DictationPhase): Notification {
        val stop = serviceIntent(context, DictationControl.ACTION_STOP, 20)
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_tw)
            .setContentTitle("TypeWhisper")
            .setContentText(
                when (phase) {
                    DictationPhase.Recording -> "Listening…"
                    DictationPhase.Finishing -> "Stopping…"
                    DictationPhase.Idle -> "Ready"
                },
            )
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_stat_tw, "Stop", stop)
            .build()
    }

    /**
     * The ongoing notice while a first dictation quietly installs the recogniser.
     * Reuses [ID_ONGOING] so the user watches the download inside the same
     * notification the recording already owns, rather than a second one appearing.
     */
    fun modelDownload(context: Context, percent: Int): Notification {
        val stop = serviceIntent(context, DictationControl.ACTION_STOP, 21)
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_tw)
            .setContentTitle("Getting the on-device model ready")
            .setContentText("Downloaded $percent% — dictation starts on its own")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_stat_tw, "Stop", stop)
            .build()
    }

    /**
     * The paste nudge reveals the text inline and — deliberately — carries no content
     * intent: tapping dismisses it right there, because opening this app would yank the
     * user out of the field they are dictating into. The text is already on the clipboard.
     *
     * [note] is the one line explaining anything unusual about how the text was produced
     * (a fallback to another engine, say). It is shown above the transcript rather than
     * replacing it, because the user still has to be able to see what they are pasting.
     *
     * When the voice keyboard is enabled but not the active one, the nudge gains a second
     * action that opens the system input-method picker: one tap turns the next dictation
     * from clipboard-and-paste into direct insertion.
     */
    fun pasteNudge(
        context: Context,
        text: String,
        note: String? = null,
        offerTranslate: Boolean = false,
    ): Notification {
        val again = activityIntent(context, 31, autoStart = true)
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_tw)
            .setContentTitle("Copied — long-press in the field to paste")
            .setContentText(note ?: text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (note == null) text else "$note\n$text",
                ),
            )
            .setAutoCancel(true)
            .addAction(R.drawable.ic_stat_tw, "Dictate again", again)
        if (offerTranslate) {
            val translate = PendingIntent.getService(
                context,
                33,
                Intent(context, RecordingService::class.java).setAction(DictationControl.ACTION_TRANSLATE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(R.drawable.ic_stat_tw, "Translate", translate)
        }
        if (ImeSetup.isEnabled(context) && !ImeSetup.isActive(context)) {
            val picker = PendingIntent.getActivity(
                context,
                32,
                ImeSetup.pickerIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(R.drawable.ic_mic, "Use voice keyboard", picker)
        }
        return builder.build()
    }

    /** The result of the Translate action: same delivery shape, honest about its origin. */
    fun translation(context: Context, text: String, targetLabel: String): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_tw)
            .setContentTitle("Translated to $targetLabel — long-press in the field to paste")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        return builder.build()
    }

    fun failure(context: Context, message: String): Notification {
        val open = activityIntent(context, 40)
        return NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_tw)
            .setContentTitle("TypeWhisper")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RecordingService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun activityIntent(
        context: Context,
        requestCode: Int,
        autoStart: Boolean = false,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (autoStart) putExtra(MainActivity.EXTRA_AUTO_START_DICTATION, true)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
