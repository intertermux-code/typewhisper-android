package com.typewhisper.android.capture

import android.media.AudioDeviceInfo

/**
 * Labels for the microphone list the settings screen shows.
 *
 * A device is named by its type plus id rather than `productName`, because on many
 * OEM images every built-in array reports the same product name and the user cannot
 * tell the entries apart. The id is what [android.media.AudioRecord.setPreferredDevice]
 * is matched on later, so it has to be visible next to the label.
 */
public object MicInputs {
    /** Sentinel for "let the platform route the audio", the default. */
    public const val DEFAULT_DEVICE_ID: Int = -1

    public fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in mic"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in earpiece"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE headset"
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE broadcast"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line in (analog)"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line in (digital)"
        AudioDeviceInfo.TYPE_TELEPHONY -> "Phone call audio"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "System capture"
        AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
        AudioDeviceInfo.TYPE_FM_TUNER -> "FM tuner"
        AudioDeviceInfo.TYPE_DOCK -> "Dock"
        AudioDeviceInfo.TYPE_BUS -> "Bus"
        AudioDeviceInfo.TYPE_AUX_LINE -> "Aux line"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_TV_TUNER -> "TV tuner"
        AudioDeviceInfo.TYPE_UNKNOWN -> "Unknown device"
        else -> "Device type $type"
    }

    public fun label(type: Int, id: Int): String = "${typeName(type)} · id $id"

    /** The label for [DEFAULT_DEVICE_ID], which is not a real device. */
    public const val DEFAULT_LABEL: String = "Default"
}
