package com.typewhisper.android.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.typewhisper.android.core.NormalizationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

internal val Context.typeWhisperPrefs: DataStore<Preferences> by preferencesDataStore(name = "tw_settings")

/**
 * How the bubble's mic button and the voice keyboard's mic key turn a dictation on and off.
 *
 * [TapToToggle] is the original one-tap-each-way gesture. [PushToTalk] records while the
 * finger is down and stops when it comes up, which is the gesture people expect from a
 * transceiver. Quick Settings tiles are tap-only whatever this says — the platform gives a
 * tile a click, not a press.
 */
public enum class DictationMode {
    TapToToggle,
    PushToTalk;

    internal companion object {
        /** Unset and unrecognised both mean push-to-talk: it is the app's default gesture. */
        fun fromStorage(value: String?): DictationMode =
            entries.firstOrNull { it.name == value } ?: PushToTalk
    }
}

public object PrefKeys {
    public val ENGINE_ID: Preferences.Key<String> = stringPreferencesKey("engine_id")
    public val LANGUAGE: Preferences.Key<String> = stringPreferencesKey("language")
    public val OVERLAY_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("overlay_trigger")
    public val DICTATION_MODE: Preferences.Key<String> = stringPreferencesKey("dictation_mode")
    public val ACTIVE_PROFILE_ID: Preferences.Key<String> = stringPreferencesKey("active_profile_id")
    public val ONBOARDING_COMPLETE: Preferences.Key<Boolean> = booleanPreferencesKey("onboarding_complete")
    public val DOWNLOAD_OVER_CELLULAR: Preferences.Key<Boolean> = booleanPreferencesKey("download_over_cellular")

    // Same keys the macOS app keeps in UserDefaults, so :core can read them unchanged.
    public val NUMBER_NORMALIZATION_ENABLED: Preferences.Key<Boolean> =
        booleanPreferencesKey(NormalizationSettings.NUMBER_NORMALIZATION_ENABLED)
    public val NUMBER_NORMALIZATION_MINIMUM_VALUE: Preferences.Key<Int> =
        intPreferencesKey(NormalizationSettings.NUMBER_NORMALIZATION_MINIMUM_VALUE)

    /** Prefix for the per-engine key held in the encrypted store, never in DataStore. */
    public const val API_KEY_PREFIX: String = "api_key_"
}

/**
 * Non-secret settings. Anything that could spend money or reach a user's audio lives in
 * [SecretStore] instead, which is why no API key appears in this file.
 */
public class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

    private val preferences: Flow<Preferences> = dataStore.data.catch { cause ->
        // A corrupt preferences file must not take dictation down; fall back to defaults.
        if (cause is IOException) emit(emptyPreferences()) else throw cause
    }

    public val engineId: Flow<String> = preferences.map { it[PrefKeys.ENGINE_ID] ?: DEFAULT_ENGINE_ID }

    public val language: Flow<String> = preferences.map { it[PrefKeys.LANGUAGE] ?: DEFAULT_LANGUAGE }

    public val overlayEnabled: Flow<Boolean> = preferences.map { it[PrefKeys.OVERLAY_ENABLED] ?: true }

    public val dictationMode: Flow<DictationMode> =
        preferences.map { DictationMode.fromStorage(it[PrefKeys.DICTATION_MODE]) }

    public val activeProfileId: Flow<String?> = preferences.map { it[PrefKeys.ACTIVE_PROFILE_ID] }

    public val onboardingComplete: Flow<Boolean> =
        preferences.map { it[PrefKeys.ONBOARDING_COMPLETE] ?: false }

    public val downloadOverCellular: Flow<Boolean> =
        preferences.map { it[PrefKeys.DOWNLOAD_OVER_CELLULAR] ?: false }

    public suspend fun setEngineId(value: String) {
        dataStore.edit { it[PrefKeys.ENGINE_ID] = value }
    }

    public suspend fun setLanguage(value: String) {
        dataStore.edit { it[PrefKeys.LANGUAGE] = value }
    }

    public suspend fun setOverlayEnabled(value: Boolean) {
        dataStore.edit { it[PrefKeys.OVERLAY_ENABLED] = value }
    }

    public suspend fun setDictationMode(value: DictationMode) {
        dataStore.edit { it[PrefKeys.DICTATION_MODE] = value.name }
    }

    public suspend fun setActiveProfileId(value: String?) {
        dataStore.edit { preferences ->
            if (value == null) preferences.remove(PrefKeys.ACTIVE_PROFILE_ID) else preferences[PrefKeys.ACTIVE_PROFILE_ID] = value
        }
    }

    public suspend fun setOnboardingComplete(value: Boolean) {
        dataStore.edit { it[PrefKeys.ONBOARDING_COMPLETE] = value }
    }

    public suspend fun setDownloadOverCellular(value: Boolean) {
        dataStore.edit { it[PrefKeys.DOWNLOAD_OVER_CELLULAR] = value }
    }

    public suspend fun setNumberNormalizationEnabled(value: Boolean) {
        dataStore.edit { it[PrefKeys.NUMBER_NORMALIZATION_ENABLED] = value }
    }

    public suspend fun setNumberNormalizationMinimumValue(value: Int) {
        dataStore.edit { it[PrefKeys.NUMBER_NORMALIZATION_MINIMUM_VALUE] = value }
    }

    /**
     * Snapshot for :core, which is JVM-only and reads its configuration through
     * [NormalizationSettings] rather than through DataStore. Unset keys stay unset so the
     * service applies its own defaults instead of guessing from a stored zero.
     */
    public suspend fun normalizationSettings(): NormalizationSettings {
        val snapshot = preferences.first()
        return SnapshotNormalizationSettings(
            enabled = snapshot[PrefKeys.NUMBER_NORMALIZATION_ENABLED],
            minimumValue = snapshot[PrefKeys.NUMBER_NORMALIZATION_MINIMUM_VALUE],
        )
    }

    public companion object {
        public const val DEFAULT_ENGINE_ID: String = "whispercpp"
        public const val DEFAULT_LANGUAGE: String = "auto"

        public fun forContext(context: Context): PreferencesRepository =
            PreferencesRepository(context.typeWhisperPrefs)
    }
}

private class SnapshotNormalizationSettings(
    private val enabled: Boolean?,
    private val minimumValue: Int?,
) : NormalizationSettings {

    override fun integer(key: String): Int? =
        if (key == NormalizationSettings.NUMBER_NORMALIZATION_MINIMUM_VALUE) minimumValue else null

    override fun boolean(key: String): Boolean? =
        if (key == NormalizationSettings.NUMBER_NORMALIZATION_ENABLED) enabled else null
}
