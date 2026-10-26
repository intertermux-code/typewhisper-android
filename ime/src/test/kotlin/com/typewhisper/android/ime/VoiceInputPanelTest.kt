package com.typewhisper.android.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VoiceInputPanelTest {

    @Test
    fun onlyListeningOffersStop() {
        assertEquals(R.string.mic_stop, VoiceInputPanel.micLabelRes(VoiceState.Listening))
        for (state in listOf(VoiceState.Idle, VoiceState.Working, VoiceState.Error)) {
            assertEquals(R.string.mic_start, VoiceInputPanel.micLabelRes(state))
        }
    }

    @Test
    fun everyStateHasItsOwnStatusLabel() {
        val labels = VoiceState.entries.map { VoiceInputPanel.statusLabelRes(it) }
        assertEquals(labels.size, labels.distinct().size)
    }

    @Test
    fun pushToTalkNamesTheGestureOnTheKeyAndTheIdleLine() {
        // The mode has to be readable from the keyboard alone, without opening Settings.
        assertEquals(R.string.mic_release, VoiceInputPanel.micLabelRes(VoiceState.Listening, true))
        assertNotEquals(
            VoiceInputPanel.statusLabelRes(VoiceState.Idle),
            VoiceInputPanel.statusLabelRes(VoiceState.Idle, true),
        )
    }

    @Test
    fun pushToTalkStillStopsOfferingASendOnceTheKeyIsReleased() {
        for (state in listOf(VoiceState.Idle, VoiceState.Working, VoiceState.Error)) {
            assertEquals(R.string.mic_start, VoiceInputPanel.micLabelRes(state, true))
        }
    }
}
