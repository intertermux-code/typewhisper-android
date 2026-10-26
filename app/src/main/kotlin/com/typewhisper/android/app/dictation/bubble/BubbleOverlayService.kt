package com.typewhisper.android.app.dictation.bubble

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.core.content.getSystemService
import com.typewhisper.android.app.TypeWhisperApplication
import com.typewhisper.android.app.dictation.DictationControl
import com.typewhisper.android.ime.ImeSetup
import com.typewhisper.android.storage.DictationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A floating, draggable dictation button drawn over every other app.
 *
 * This is a started service rather than an activity because the bubble must
 * persist after the app's UI closes — Android has no global hotkey, so a
 * resident overlay is the closest equivalent. The window uses the
 * [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] token, the only window
 * type permitted to sit above foreign apps, and it is offered solely when
 * [canShowOverlays] reports the special-access permission is granted. When it
 * is not, callers never start this service: the tile and notification triggers
 * cover those users, so there is no crash and no permission nag loop.
 */
class BubbleOverlayService : android.app.Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bubble: BubbleView? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        if (!canShowOverlays(this)) {
            stopSelf()
            return
        }
        val windowManager = getSystemService<WindowManager>()!!
        val view = BubbleView(
            this,
            object : BubbleView.Listener {
                override fun toggle() = DictationControl.toggle(this@BubbleOverlayService)

                override fun pressStart() = DictationControl.start(this@BubbleOverlayService)

                override fun pressEnd(discard: Boolean) {
                    if (discard) {
                        DictationControl.discard(this@BubbleOverlayService)
                    } else {
                        DictationControl.stop(this@BubbleOverlayService)
                    }
                }

                override fun routeToKeyboard() = this@BubbleOverlayService.routeIntoKeyboard()
            },
        )
        windowManager.addView(view, view.layoutParams())
        bubble = view
        scope.launch {
            DictationControl.phase.collect { phase -> bubble?.phase = phase }
        }
        scope.launch {
            TypeWhisperApplication.container(this@BubbleOverlayService)
                .preferences
                .dictationMode
                .collect { mode -> bubble?.pushToTalk = mode == DictationMode.PushToTalk }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-check on every start: the permission can be revoked after onCreate,
        // and adding an overlay window without it crashes.
        if (!canShowOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        bubble?.let { getSystemService<WindowManager>()?.removeView(it) }
        bubble = null
        scope.cancel()
        super.onDestroy()
    }

    /**
     * The hold gesture's promise: get the user one tap away from direct insertion.
     * An enabled voice keyboard needs only the picker; one that was never switched
     * on needs its settings screen first, and both are system windows the overlay
     * can raise.
     */
    private fun routeIntoKeyboard() {
        if (ImeSetup.isEnabled(this)) {
            ImeSetup.showPicker(this)
        } else {
            runCatching {
                startActivity(ImeSetup.enableIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    companion object {
        /** SYSTEM_ALERT_WINDOW is special access, not a runtime permission. */
        fun canShowOverlays(context: Context): Boolean =
            android.provider.Settings.canDrawOverlays(context)

        fun start(context: Context) {
            if (!canShowOverlays(context)) return
            // Plain startService: this is a background service hosting a window, not
            // a foreground one — startForegroundService would demand a startForeground
            // call we never make. It is only ever started from the foreground app.
            context.startService(Intent(context, BubbleOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleOverlayService::class.java))
        }
    }
}
