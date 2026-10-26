package com.typewhisper.android.capture

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class MicInputsTest {

    @Test
    fun `known types get a readable name`() {
        assertEquals("Built-in mic", MicInputs.typeName(AudioDeviceInfo.TYPE_BUILTIN_MIC))
        assertEquals("Bluetooth headset", MicInputs.typeName(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
        assertEquals("USB headset", MicInputs.typeName(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertEquals("Unknown device", MicInputs.typeName(AudioDeviceInfo.TYPE_UNKNOWN))
    }

    @Test
    fun `unlisted types fall back to the raw number`() {
        assertEquals("Device type 999", MicInputs.typeName(999))
    }

    @Test
    fun `label carries the id the recorder is matched on`() {
        assertEquals("Built-in mic · id 7", MicInputs.label(AudioDeviceInfo.TYPE_BUILTIN_MIC, 7))
    }
}
