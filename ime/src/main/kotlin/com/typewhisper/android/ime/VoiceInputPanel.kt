package com.typewhisper.android.ime

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.typewhisper.android.capture.PushToTalkPress
import com.typewhisper.android.capture.ReleaseAction

/** Kept public: the service's insertion callbacks exchange this type across module edges. */
enum class VoiceState { Idle, Listening, Working, Error }

/**
 * Plain-Views input panel. No Compose here on purpose: the input view is a
 * window-managed surface handed to the system by `onCreateInputView`, and
 * Compose-in-IME means juggling a CompositionContext against IME lifecycle
 * callbacks for what is one button and a status line.
 *
 * The mic key answers to whichever gesture the user picked. Push to talk consumes the whole
 * touch stream, so the button's own pressed state is driven by hand — without that the key
 * would look untouched while it was recording.
 */
internal class VoiceInputPanel private constructor(
    val root: View,
    private val status: TextView,
    private val micButton: Button,
) {

    /** What a gesture on the mic key asks the dictation loop to do. */
    interface Listener {
        /** Tap mode, and the accessibility click: start or stop, whichever is owed. */
        fun onToggle()

        /** Push to talk: the finger went down on the key. */
        fun onPressStart()

        /** Push to talk: the finger came up, or the gesture was taken away. */
        fun onPressEnd(discard: Boolean)
    }

    private var listener: Listener? = null
    private var press: PushToTalkPress? = null
    private var downTime = 0L
    private var state = VoiceState.Idle

    /**
     * The gesture this key answers to. Defaults to push to talk because that is the mode the
     * app ships with, and the settings flow only replaces it a moment after the panel exists
     * — long before a finger can land on the key.
     *
     * In push-to-talk mode the touch listener consumes the whole stream and no click is ever
     * delivered, so the click listener set alongside it stays as the route an accessibility
     * service uses when it can only send a click.
     */
    var pushToTalk: Boolean = true
        set(value) {
            field = value
            if (!value) {
                // A mode change mid-press must not leave the microphone open.
                endPress()
            }
            render(state)
        }

    @SuppressLint("ClickableViewAccessibility")
    fun setListener(listener: Listener) {
        this.listener = listener
        micButton.setOnClickListener { listener.onToggle() }
        micButton.setOnTouchListener { view, event ->
            if (!pushToTalk) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = event.eventTime
                    press = PushToTalkPress().apply { down() }
                    view.isPressed = true
                    listener.onPressStart()
                }

                // ACTION_CANCEL is the same ending here: the gesture was stolen, but a
                // stolen gesture that was held is still a dictation the user meant to send.
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    endPress(event.eventTime - downTime)
                }
            }
            true
        }
    }

    /** Settles the in-flight press, if there is one. Safe to call from anywhere. */
    private fun endPress(elapsedMs: Long = 0L) {
        val press = press ?: return
        this.press = null
        micButton.isPressed = false
        val action = press.release(elapsedMs)
        if (action == ReleaseAction.None) return
        listener?.onPressEnd(discard = action == ReleaseAction.Discard)
    }

    fun render(state: VoiceState) {
        this.state = state
        status.setText(statusLabelRes(state, pushToTalk))
        micButton.setText(micLabelRes(state, pushToTalk))
    }

    fun showError(message: String) {
        status.text = message
        micButton.setText(R.string.mic_start)
    }

    /** A plain status line the user can wait on — no error styling, no button change. */
    fun showStatus(message: String) {
        status.text = message
    }

    companion object {

        fun inflate(context: Context): VoiceInputPanel {
            val root = LayoutInflater.from(context).inflate(R.layout.voice_input, null)
            return VoiceInputPanel(
                root = root,
                status = root.findViewById(R.id.tw_status),
                micButton = root.findViewById(R.id.tw_mic),
            )
        }

        fun micLabelRes(state: VoiceState, pushToTalk: Boolean = false): Int = when (state) {
            // While the key is held the button has to say what releasing will do.
            VoiceState.Listening -> if (pushToTalk) R.string.mic_release else R.string.mic_stop
            VoiceState.Idle, VoiceState.Working, VoiceState.Error -> R.string.mic_start
        }

        fun statusLabelRes(state: VoiceState, pushToTalk: Boolean = false): Int = when (state) {
            VoiceState.Idle -> if (pushToTalk) R.string.state_idle_hold else R.string.state_idle
            VoiceState.Listening -> R.string.state_listening
            VoiceState.Working -> R.string.state_working
            VoiceState.Error -> R.string.state_error
        }
    }
}
