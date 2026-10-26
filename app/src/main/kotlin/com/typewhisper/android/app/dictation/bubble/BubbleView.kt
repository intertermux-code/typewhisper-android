package com.typewhisper.android.app.dictation.bubble

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import com.typewhisper.android.app.R
import com.typewhisper.android.app.dictation.DictationPhase
import com.typewhisper.android.capture.PushToTalkPress
import com.typewhisper.android.capture.ReleaseAction
import kotlin.math.abs

/**
 * The single button inside the overlay bubble, and the gesture that drives a dictation from
 * it. Drag it anywhere; then either tap once to start and once to stop, or — the default —
 * hold it to record and let go to send.
 *
 * Everything about the touch stream exists to make one press of this button do exactly what
 * it looks like it will:
 *
 *  - the window is *not* touch modal, so the bubble never swallows a touch meant for the app
 *    underneath it;
 *  - the window is only repositioned when the position actually changed, because
 *    `updateViewLayout` is a synchronous call to the window manager and there is no point
 *    paying for one per event;
 *  - a press that is long enough to be a stop is never mistaken for the tap-mode
 *    long-press shortcut;
 *  - `ACTION_CANCEL` ends the press like a release does, so the microphone cannot be left
 *    recording by a gesture the system stole.
 */
@SuppressLint("ViewConstructor")
class BubbleView(
    context: Context,
    private val listener: Listener,
) : ImageView(context) {

    /** What a gesture on the bubble asks the dictation loop to do. */
    interface Listener {
        /** Tap mode: start or stop, whichever the phase says. */
        fun toggle()

        /** Push to talk: the finger went down, open the microphone. */
        fun pressStart()

        /** Push to talk: the finger came up. [discard] when the press was too short to mean. */
        fun pressEnd(discard: Boolean)

        /** Tap mode's hold-the-idle-bubble shortcut into the voice keyboard. */
        fun routeToKeyboard()
    }

    private val density = resources.displayMetrics.density
    private val sizePx = (56 * density).toInt()
    private val padPx = (12 * density).toInt()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /**
     * How far a push-to-talk press may wander before it counts as moving the bubble rather
     * than talking. Three times the tap threshold: a thumb held still for seconds drifts,
     * and losing the utterance over that would be a worse complaint than a stray stop.
     */
    private val dragSlopPx = touchSlop * DRAG_SLOP_FACTOR
    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()

    private val colorIdle = Color.parseColor(COLOR_IDLE)
    private val colorRecording = Color.parseColor(COLOR_RECORDING)
    private val colorFinishing = Color.parseColor(COLOR_FINISHING)

    private val backgroundShape = GradientDrawable().apply { shape = GradientDrawable.OVAL }

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var startX = 0
    private var startY = 0
    private var movedFar = false
    private var press: PushToTalkPress? = null

    /** The gesture this bubble answers to, kept in step with the setting. */
    var pushToTalk: Boolean = true

    /** Mirrors the dictation loop's phase onto the button. */
    var phase = DictationPhase.Idle
        set(value) {
            field = value
            render()
        }

    init {
        setImageResource(R.drawable.ic_mic)
        scaleType = ScaleType.CENTER_INSIDE
        setPadding(padPx, padPx, padPx, padPx)
        background = backgroundShape
        render()
    }

    fun layoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        // TYPE_APPLICATION_OVERLAY is the only token allowed above other apps.
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Not focusable: the bubble must never steal input from the field the user is
        // dictating into. Not touch modal: without this flag the window is modal for touch
        // and every tap that lands anywhere else on the screen — the notification's Stop
        // action included — is eaten by the bubble instead of reaching its own window.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = resources.displayMetrics.widthPixels - sizePx - padPx
        y = resources.displayMetrics.heightPixels / 3
    }

    private fun render() {
        val held = pushToTalk && press != null
        val recording = phase == DictationPhase.Recording || (held && phase == DictationPhase.Idle)
        backgroundShape.setColor(
            when {
                phase == DictationPhase.Finishing -> colorFinishing
                recording -> colorRecording
                else -> colorIdle
            },
        )
        // Push to talk keeps the mic glyph while the finger is down: the button says what it
        // is doing, and releasing is what stops it.
        setImageResource(
            if (phase == DictationPhase.Idle || (pushToTalk && held)) R.drawable.ic_mic else R.drawable.ic_stop,
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val params = layoutParams as? WindowManager.LayoutParams ?: return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downTime = event.eventTime
                startX = params.x
                startY = params.y
                movedFar = false
                if (pushToTalk) {
                    press = PushToTalkPress().apply { down() }
                    scalePressed(true)
                    listener.pressStart()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    movedFar = true
                    if (abs(dx) > dragSlopPx || abs(dy) > dragSlopPx) press?.drag()
                    moveWindow(params, startX + dx.toInt(), startY + dy.toInt())
                }
            }

            MotionEvent.ACTION_UP -> {
                scalePressed(false)
                val press = press
                if (press != null) {
                    this.press = null
                    when (press.release(event.eventTime - downTime)) {
                        ReleaseAction.StopAndTranscribe -> listener.pressEnd(discard = false)
                        ReleaseAction.Discard -> listener.pressEnd(discard = true)
                        ReleaseAction.None -> Unit
                    }
                } else if (!movedFar) {
                    // While a dictation is running every press is a stop, however long the
                    // finger stayed down — the keyboard shortcut is for an idle bubble only.
                    val shortcutAvailable = phase == DictationPhase.Idle && !pushToTalk
                    if (shortcutAvailable && event.eventTime - downTime >= longPressMs) {
                        listener.routeToKeyboard()
                    } else {
                        listener.toggle()
                    }
                }
                render()
                performClick()
            }

            // A stolen gesture must never leave the microphone hot, so it ends the press
            // exactly the way a release does.
            MotionEvent.ACTION_CANCEL -> {
                scalePressed(false)
                val press = press
                if (press != null) {
                    this.press = null
                    if (press.release(event.eventTime - downTime) == ReleaseAction.StopAndTranscribe) {
                        listener.pressEnd(discard = false)
                    } else {
                        listener.pressEnd(discard = true)
                    }
                }
                render()
            }
        }
        return true
    }

    private fun moveWindow(params: WindowManager.LayoutParams, x: Int, y: Int) {
        val metrics = resources.displayMetrics
        val clampedX = x.coerceIn(0, metrics.widthPixels - sizePx)
        val clampedY = y.coerceIn(0, metrics.heightPixels - sizePx)
        if (params.x == clampedX && params.y == clampedY) return
        params.x = clampedX
        params.y = clampedY
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?.updateViewLayout(this, params)
    }

    /**
     * The pressed state. Scaled down rather than up: the window is exactly the button's
     * size, so anything larger than the view would be clipped by its own window.
     */
    private fun scalePressed(pressed: Boolean) {
        val target = if (pressed) PRESSED_SCALE else 1f
        animate().scaleX(target).scaleY(target).setDuration(90L).start()
        render()
    }

    private companion object {
        const val COLOR_IDLE = "#FF1F6FEB"
        const val COLOR_RECORDING = "#FFD04141"
        const val COLOR_FINISHING = "#FF8E8E93"
        const val DRAG_SLOP_FACTOR = 3
        const val PRESSED_SCALE = 0.9f
    }
}
