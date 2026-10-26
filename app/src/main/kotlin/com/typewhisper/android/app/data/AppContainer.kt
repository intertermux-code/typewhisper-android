package com.typewhisper.android.app.data

import android.app.Application
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.content.getSystemService
import com.typewhisper.android.capture.MicInputs
import com.typewhisper.android.engines.mac.MacEnginePlugin
import com.typewhisper.android.engines.whispercpp.WhisperModel
import com.typewhisper.android.storage.HistoryRepository
import com.typewhisper.android.storage.PreferencesRepository
import com.typewhisper.android.storage.ProfileRepository
import com.typewhisper.android.storage.SecretStore
import com.typewhisper.android.storage.SubstitutionRepository
import com.typewhisper.android.storage.TypeWhisperDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Hand-built dependency graph for the app. Hilt is in the catalog but the app has no
 * injection sites yet, and a plain container keeps the engine factories and the settings
 * screens reading the same repository instances.
 */
class AppContainer(private val application: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences: PreferencesRepository = PreferencesRepository.forContext(application)
    val engineConfig: EngineConfigRepository = EngineConfigRepository(application)

    /**
     * Snapshots of the values the engines need synchronously. Engine factories are called
     * from the recording path, where suspending on a DataStore read would stall capture.
     */
    val macHost: StateFlow<String> = engineConfig.macHost
        .stateIn(scope, SharingStarted.Eagerly, EngineConfigRepository.DEFAULT_MAC_HOST)
    val macPort: StateFlow<Int> = engineConfig.macPort
        .stateIn(scope, SharingStarted.Eagerly, MacEnginePlugin.DEFAULT_RELAY_PORT)

    /** Snapshot of the user's own OpenAI-compatible endpoint; "" until they type one. */
    val customBaseUrl: StateFlow<String> = engineConfig.customBaseUrl
        .stateIn(scope, SharingStarted.Eagerly, "")

    /**
     * The Mac engine's base URL, or null while the relay host is empty. Engine factories
     * read the [macHost] / [macPort] snapshots, so this cannot suspend.
     */
    fun macBaseUrl(): String? = EngineConfigRepository.macBaseUrl(macHost.value, macPort.value)

    /** Snapshot of the on-device model choice, for the same synchronous reason as [macHost]. */
    val whisperModelId: StateFlow<String> = engineConfig.whisperModelId
        .stateIn(scope, SharingStarted.Eagerly, WhisperModel.DEFAULT.id)

    /**
     * The stored mic id resolved to a device handle. Enumerating audio devices is a binder
     * call, so it is cached here on the container's dispatcher and the recording path only
     * reads the value. An unplugged device leaves a stale handle behind, which is harmless:
     * AudioRecord refuses the hint and falls back to its own routing.
     */
    val micDevice: StateFlow<AudioDeviceInfo?> = engineConfig.micDeviceId.map { id ->
        if (id == MicInputs.DEFAULT_DEVICE_ID) {
            null
        } else {
            audioManager().getDevices(AudioManager.GET_DEVICES_INPUTS)
                .firstOrNull { it.id == id && it.isSource }
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * First access decrypts a file through the Keystore, which is slow disk I/O: reach for
     * this only from a background dispatcher, never from a composable.
     */
    val secrets: SecretStore by lazy { SecretStore(application) }

    /**
     * Resolving a Room DAO opens the database file, so the whole storage graph is built on
     * IO and handed out through a suspend call — no screen can accidentally do it on main.
     */
    private val storage: Deferred<Storage> = scope.async(Dispatchers.IO) {
        val database = TypeWhisperDatabase.create(application)
        Storage(HistoryRepository(database), SubstitutionRepository(database), ProfileRepository(database))
    }

    suspend fun history(): HistoryRepository = withContext(Dispatchers.IO) { storage.await().history }

    suspend fun substitutions(): SubstitutionRepository =
        withContext(Dispatchers.IO) { storage.await().substitutions }

    suspend fun profiles(): ProfileRepository =
        withContext(Dispatchers.IO) { storage.await().profiles }

    fun audioManager(): AudioManager = requireNotNull(application.getSystemService())

    private class Storage(
        val history: HistoryRepository,
        val substitutions: SubstitutionRepository,
        val profiles: ProfileRepository,
    )
}
