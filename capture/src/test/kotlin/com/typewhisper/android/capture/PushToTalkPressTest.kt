package com.typewhisper.android.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushToTalkPressTest {

    @Test
    fun aDeliberateHoldStopsAndTranscribes() {
        val press = PushToTalkPress()
        press.down()
        assertTrue(press.isActive)
        assertEquals(ReleaseAction.StopAndTranscribe, press.release(PushToTalkPress.MIN_HOLD_MS))
        assertFalse(press.isActive)
    }

    @Test
    fun aShorterHoldThanTheMinimumIsDiscarded() {
        val press = PushToTalkPress()
        press.down()
        assertEquals(ReleaseAction.Discard, press.release(PushToTalkPress.MIN_HOLD_MS - 1))
    }

    @Test
    fun aDragIsNeverADictation() {
        val press = PushToTalkPress()
        press.down()
        press.drag()
        assertEquals(ReleaseAction.Discard, press.release(5_000))
    }

    @Test
    fun aStraySecondReleaseCannotTouchTheNextPress() {
        val press = PushToTalkPress()
        press.down()
        assertEquals(ReleaseAction.StopAndTranscribe, press.release(1_000))
        // The trailing ACTION_CANCEL of the same gesture must not do anything at all.
        assertEquals(ReleaseAction.None, press.release(1_000))
    }

    @Test
    fun aNewPressStartsOverAfterThePreviousOneWasDiscarded() {
        val press = PushToTalkPress()
        press.down()
        assertEquals(ReleaseAction.Discard, press.release(40))
        press.down()
        assertFalse(press.isDragging)
        assertEquals(ReleaseAction.StopAndTranscribe, press.release(400))
    }
}
