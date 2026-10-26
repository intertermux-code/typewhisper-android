package com.typewhisper.android.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.typewhisper.android.capture.MicInputs
import com.typewhisper.android.engines.mac.MacEnginePlugin
import com.typewhisper.android.engines.whispercpp.WhisperModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.engineConfigStore: DataStore<Preferences> by preferencesDataStore(name = "tw_engine_config")

/**
 * Per-engine configuration that is neither a secret nor a global app preference:
 * where the Mac relay lives, which OpenAI-compatible endpoint the user's own engine
 * talks to, and which microphone to record from.
 *
 * The relay address is a plain host:port and goes to the Mac's own server, which
 * authenticates the token separately, so it belongs in plain preferences — the token
 * itself never appears here (see [SecretStore][com.typewhisper.android.storage.SecretStore]).
 * None of these have a built-in value: whoever builds this app types their own.
 */
class EngineConfigRepository(context: Context) {

    private val dataStore = context.engineConfigStore

    private val preferences: Flow<Preferences> = dataStore.data.catch { cause ->
        if (cause is IOException) emit(emptyPreferences()) else throw cause
    }

    val macHost: Flow<String> = preferences.map { it[Keys.MAC_HOST] ?: DEFAULT_MAC_HOST }

    val macPort: Flow<Int> = preferences.map { it[Keys.MAC_PORT] ?: MacEnginePlugin.DEFAULT_RELAY_PORT }

    /** The OpenAI-compatible endpoint the custom engine talks to; empty until the user types one. */
    val customBaseUrl: Flow<String> = preferences.map { it[Keys.CUSTOM_BASE_URL] ?: "" }

    val micDeviceId: Flow<Int> = preferences.map { it[Keys.MIC_DEVICE_ID] ?: MicInputs.DEFAULT_DEVICE_ID }

    /** Which ggml checkpoint the on-device engine loads; see [WhisperModel.fromId]. */
    val whisperModelId: Flow<String> =
        preferences.map { it[Keys.WHISPER_MODEL_ID] ?: WhisperModel.DEFAULT.id }

    suspend fun setMacHost(value: String) {
        dataStore.edit { it[Keys.MAC_HOST] = value.trim() }
    }

    suspend fun setCustomBaseUrl(value: String) {
        dataStore.edit { it[Keys.CUSTOM_BASE_URL] = value.trim() }
    }

    suspend fun setMacPort(value: Int) {
        dataStore.edit { it[Keys.MAC_PORT] = value.coerceIn(1, 65_535) }
    }

    /** [MicInputs.DEFAULT_DEVICE_ID] restores platform routing. */
    suspend fun setMicDeviceId(value: Int) {
        dataStore.edit { it[Keys.MIC_DEVICE_ID] = value }
    }

    suspend fun setWhisperModelId(value: String) {
        dataStore.edit { it[Keys.WHISPER_MODEL_ID] = WhisperModel.fromId(value).id }
    }

    /**
     * The URL the Mac engine talks to, or null while the relay host is empty.
     * Callers must treat null as "not set up yet" and say so; there is no default Mac.
     */
    suspend fun macBaseUrl(): String? = macBaseUrl(macHost.first(), macPort.first())

    companion object {
        /** No Mac is assumed: the user types the address in Settings before the engine works. */
        const val DEFAULT_MAC_HOST: String = ""

        /** Null when [host] is blank, so an unset relay never becomes a bogus URL. */
        fun macBaseUrl(host: String, port: Int): String? =
            if (host.isBlank()) null else "http://${host.trim()}:$port"
    }

    private object Keys {
        val MAC_HOST = stringPreferencesKey("mac_relay_host")
        val MAC_PORT = intPreferencesKey("mac_relay_port")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        val MIC_DEVICE_ID = intPreferencesKey("mic_device_id")
        val WHISPER_MODEL_ID = stringPreferencesKey("whisper_model_id")
    }
}
