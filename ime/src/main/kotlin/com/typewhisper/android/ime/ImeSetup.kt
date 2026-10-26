package com.typewhisper.android.ime

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * The system never switches on a third-party keyboard by itself: the user has to enable
 * it in Settings and then pick it once. These are the helpers the app's onboarding uses
 * to open those two screens and to notice when the user has finished them.
 */
object ImeSetup {

    // Not part of android.jar's public Settings surface, but the activity the system
    // picker has answered to on every AOSP-derived build; showPicker() covers the rest.
    private const val PICKER_ACTION = "android.settings.SHOW_INPUT_METHOD_PICKER"

    private const val SERVICE_CLASS = "com.typewhisper.android.ime.TypeWhisperVoiceIME"

    fun enableIntent(): Intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)

    fun pickerIntent(): Intent = Intent(PICKER_ACTION)

    fun showPicker(context: Context) {
        try {
            context.startActivity(pickerIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (missing: ActivityNotFoundException) {
            context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }

    fun isActive(context: Context): Boolean {
        val current = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        )
        return current?.substringAfterLast('/') == SERVICE_CLASS
    }

    /**
     * Whether the user has switched the keyboard on in Settings, whether or not it is the
     * active one. The bubble's long-press decides between "route into the keyboard" and
     * "go enable it", and that distinction is this value.
     */
    fun isEnabled(context: Context): Boolean =
        context.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.any { it.id.substringAfterLast('/') == SERVICE_CLASS } == true
}
