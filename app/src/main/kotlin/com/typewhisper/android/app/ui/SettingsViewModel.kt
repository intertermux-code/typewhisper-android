package com.typewhisper.android.app.ui

import android.app.Application
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typewhisper.android.app.TypeWhisperApplication
import com.typewhisper.android.app.data.AppContainer
import com.typewhisper.android.app.data.EngineConfigRepository
import com.typewhisper.android.app.engine.EngineCatalog
import com.typewhisper.android.app.engine.EngineDescriptor
import com.typewhisper.android.app.engine.OnDeviceModels
import com.typewhisper.android.capture.MicInputs
import com.typewhisper.android.core.TranscriptionNormalizationService
import com.typewhisper.android.engines.mac.MacEnginePlugin
import com.typewhisper.android.engines.whispercpp.ModelDownloadException
import com.typewhisper.android.engines.whispercpp.WhisperModel
import com.typewhisper.android.storage.DictationMode
import com.typewhisper.android.storage.PreferencesRepository
import com.typewhisper.android.storage.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One entry of the mic-input picker. */
data class MicInputOption(val id: Int, val label: String) {
    companion object {
        val DEFAULT = MicInputOption(MicInputs.DEFAULT_DEVICE_ID, MicInputs.DEFAULT_LABEL)
    }
}

/** Which of the Mac-harness states the connection test last reported. */
enum class MacTest { Idle, Testing, Reachable, ServerNotEnabled, Unreachable }

/** Everything the on-device model section shows: choice, presence on disk, in-flight download. */
data class ModelStatus(
    val modelId: String = WhisperModel.DEFAULT.id,
    val downloaded: Boolean = false,
    /** 0..1 while downloading, negative when idle. */
    val fraction: Float = -1f,
    val error: String? = null,
)

/**
 * Backs the settings screen. Everything user-visible is a [StateFlow]; each write goes to
 * the repository that owns the value, so the engine built at dictation time reads exactly
 * what the user just saw.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container: AppContainer = TypeWhisperApplication.container(application)

    val engines: List<EngineDescriptor> = EngineCatalog.entries

    val engineId: StateFlow<String> = container.preferences.engineId.stateIn(
        viewModelScope,
        SHARING,
        PreferencesRepository.DEFAULT_ENGINE_ID,
    )

    val language: StateFlow<String> = container.preferences.language.stateIn(
        viewModelScope,
        SHARING,
        PreferencesRepository.DEFAULT_LANGUAGE,
    )

    val overlayEnabled: StateFlow<Boolean> = container.preferences.overlayEnabled.stateIn(
        viewModelScope,
        SHARING,
        true,
    )

    val dictationMode: StateFlow<DictationMode> = container.preferences.dictationMode.stateIn(
        viewModelScope,
        SHARING,
        DictationMode.PushToTalk,
    )

    val macHost: StateFlow<String> = container.engineConfig.macHost.stateIn(
        viewModelScope,
        SHARING,
        EngineConfigRepository.DEFAULT_MAC_HOST,
    )

    val macPort: StateFlow<Int> = container.engineConfig.macPort.stateIn(
        viewModelScope,
        SHARING,
        MacEnginePlugin.DEFAULT_RELAY_PORT,
    )

    val customBaseUrl: StateFlow<String> = container.engineConfig.customBaseUrl.stateIn(
        viewModelScope,
        SHARING,
        "",
    )

    val micDeviceId: StateFlow<Int> = container.engineConfig.micDeviceId.stateIn(
        viewModelScope,
        SHARING,
        MicInputs.DEFAULT_DEVICE_ID,
    )

    /** Which engines have a credential in the encrypted store — never the values themselves. */
    private val _hasCredential = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val hasCredential: StateFlow<Map<String, Boolean>> = _hasCredential.asStateFlow()

    /**
     * Per engine: what the user still has to type in for it to run, or null when it can run.
     * Neither the Mac's address nor a self-hosted endpoint is knowable from the phone, so an
     * engine without one is disabled rather than left to fail mid-dictation.
     */
    val missingSetup: StateFlow<Map<String, String?>> = combine(
        container.engineConfig.macHost,
        container.engineConfig.customBaseUrl,
        _hasCredential,
    ) { host, custom, credentials ->
        EngineCatalog.entries.associate { descriptor ->
            descriptor.id to EngineCatalog.missingSetup(descriptor, host, custom, credentials[descriptor.id] == true)
        }
    }.stateIn(viewModelScope, SHARING, emptyMap())

    private val _micOptions = MutableStateFlow(listOf(MicInputOption.DEFAULT))
    val micOptions: StateFlow<List<MicInputOption>> = _micOptions.asStateFlow()

    // :storage keeps the number-normalization switches as a suspend snapshot rather than
    // flows, so the screen reads it once and then mirrors its own writes.
    private val _numberNormalizationEnabled = MutableStateFlow(true)
    val numberNormalizationEnabled: StateFlow<Boolean> = _numberNormalizationEnabled.asStateFlow()

    private val _numberMinimumValue = MutableStateFlow(DEFAULT_MINIMUM)
    val numberMinimumValue: StateFlow<Int> = _numberMinimumValue.asStateFlow()

    private val _macTest = MutableStateFlow(MacTest.Idle)
    val macTest: StateFlow<MacTest> = _macTest.asStateFlow()

    private val _macTestDetail = MutableStateFlow<String?>(null)
    val macTestDetail: StateFlow<String?> = _macTestDetail.asStateFlow()

    /** Model id exactly as the user sees it in the picker, kept in sync with storage. */
    val whisperModelId: StateFlow<String> = container.engineConfig.whisperModelId.stateIn(
        viewModelScope,
        SHARING,
        WhisperModel.DEFAULT.id,
    )

    val downloadOverCellular: StateFlow<Boolean> = container.preferences.downloadOverCellular.stateIn(
        viewModelScope,
        SHARING,
        false,
    )

    private val _modelStatus = MutableStateFlow(ModelStatus())
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    val whisperModels: List<WhisperModel> = WhisperModel.entries

    /** Profiles live in Room, which only wakes on IO, so the flow is built lazily there. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val profiles: StateFlow<List<Profile>> = container.preferences.activeProfileId
        .flatMapLatest { flow { emitAll(container.profiles().profiles) }.flowOn(Dispatchers.IO) }
        .stateIn(viewModelScope, SHARING, emptyList())

    val activeProfileId: StateFlow<String?> = container.preferences.activeProfileId.stateIn(
        viewModelScope,
        SHARING,
        null,
    )

    /**
     * Null until DataStore has answered. Guessing either way would be visible: starting
     * false flashes setup at a configured phone, starting true shows tabs for a frame on
     * a fresh one, so the host waits for the real value.
     */
    private val _onboardingComplete = MutableStateFlow<Boolean?>(null)
    val onboardingComplete: StateFlow<Boolean?> = _onboardingComplete.asStateFlow()

    init {
        refreshCredentials()
        refreshMicrophones()
        loadNormalizationSnapshot()
        refreshModelStatus()
        viewModelScope.launch {
            container.preferences.onboardingComplete.collect { _onboardingComplete.value = it }
        }
    }

    /** Whether the selected checkpoint's file is on disk; cheap stat, kept off main. */
    fun refreshModelStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val model = OnDeviceModels.currentModel(getApplication())
            _modelStatus.value = _modelStatus.value.copy(
                modelId = model.id,
                downloaded = OnDeviceModels.downloader(getApplication()).isDownloaded(model),
            )
        }
    }

    fun selectWhisperModel(id: String) {
        viewModelScope.launch {
            container.engineConfig.setWhisperModelId(id)
            refreshModelStatus()
        }
    }

    /** Fetches the selected model with progress; a refused fetch is explained, not swallowed. */
    fun downloadModel() {
        if (_modelStatus.value.fraction >= 0f) return
        _modelStatus.value = _modelStatus.value.copy(fraction = 0f, error = null)
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                OnDeviceModels.ensureReady(context) { fraction ->
                    _modelStatus.update { it.copy(fraction = fraction) }
                }
                _modelStatus.value = _modelStatus.value.copy(downloaded = true, fraction = -1f, error = null)
            } catch (e: ModelDownloadException) {
                _modelStatus.value = _modelStatus.value.copy(
                    downloaded = false,
                    fraction = -1f,
                    error = OnDeviceModels.explain(context, e),
                )
            }
        }
    }

    fun setDownloadOverCellular(value: Boolean) {
        viewModelScope.launch { container.preferences.setDownloadOverCellular(value) }
    }

    /** A named engine + language bundle, the way the Mac app keeps its contexts. */
    fun createProfile(name: String, engineId: String, language: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            container.profiles().save(
                Profile(
                    name = trimmed,
                    engineId = EngineCatalog.byId(engineId)?.id ?: PreferencesRepository.DEFAULT_ENGINE_ID,
                    language = language.trim()
                        .ifEmpty { PreferencesRepository.DEFAULT_LANGUAGE }
                        .takeIf { it != PreferencesRepository.DEFAULT_LANGUAGE },
                ),
            )
        }
    }

    fun renameProfile(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val existing = container.profiles().byId(id) ?: return@launch
            container.profiles().save(existing.copy(name = trimmed))
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            val existing = container.profiles().byId(id) ?: return@launch
            container.profiles().delete(existing)
            if (container.preferences.activeProfileId.first() == id) {
                container.preferences.setActiveProfileId(null)
            }
        }
    }

    /**
     * Applying a profile is writing the two settings it carries: dictation resolves its
     * engine and language from preferences, so a profile that stored values without
     * applying them would be a decoy.
     */
    fun applyProfile(id: String) {
        viewModelScope.launch {
            val profile = container.profiles().byId(id) ?: return@launch
            container.preferences.setEngineId(profile.engineId)
            container.preferences.setLanguage(profile.language ?: PreferencesRepository.DEFAULT_LANGUAGE)
            container.preferences.setActiveProfileId(id)
        }
    }

    fun stopUsingProfile() {
        viewModelScope.launch { container.preferences.setActiveProfileId(null) }
    }

    fun selectEngine(id: String) {
        val descriptor = EngineCatalog.byId(id) ?: return
        // An engine without its key or its address cannot work, so it is not offered — the
        // same rule the greyed-out rows apply, kept here too so a stale UI cannot bypass it.
        val missing = EngineCatalog.missingSetup(
            descriptor,
            macHost.value,
            customBaseUrl.value,
            _hasCredential.value[id] == true,
        )
        if (missing != null) return
        viewModelScope.launch { container.preferences.setEngineId(id) }
    }

    /** The value is written to the encrypted store and dropped from memory straight away. */
    fun saveCredential(id: String, value: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.secrets.saveSecret(id, value.trim()) }
            refreshCredentials()
        }
    }

    fun removeCredential(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.secrets.clearSecret(id) }
            refreshCredentials()
        }
    }

    private fun refreshCredentials() {
        viewModelScope.launch(Dispatchers.IO) {
            val store = container.secrets
            _hasCredential.value = EngineCatalog.entries.associate { it.id to store.hasSecret(it.id) }
        }
    }

    fun refreshMicrophones() {
        viewModelScope.launch(Dispatchers.IO) {
            val devices = container.audioManager()
                .getDevices(AudioManager.GET_DEVICES_INPUTS)
                .filter { it.isSource }
                .map { MicInputOption(it.id, MicInputs.label(it.type, it.id)) }
                .distinctBy { it.id }
                .sortedBy { it.label }
            _micOptions.value = listOf(MicInputOption.DEFAULT) + devices
        }
    }

    fun setMicDevice(id: Int) {
        viewModelScope.launch { container.engineConfig.setMicDeviceId(id) }
    }

    fun setLanguage(value: String) {
        val tag = value.trim()
        viewModelScope.launch {
            container.preferences.setLanguage(tag.ifEmpty { PreferencesRepository.DEFAULT_LANGUAGE })
        }
    }

    fun setMacHost(value: String) {
        viewModelScope.launch { container.engineConfig.setMacHost(value) }
    }

    fun setMacPort(value: Int) {
        viewModelScope.launch { container.engineConfig.setMacPort(value) }
    }

    fun setCustomBaseUrl(value: String) {
        viewModelScope.launch { container.engineConfig.setCustomBaseUrl(value) }
    }

    fun setOverlayEnabled(value: Boolean) {
        viewModelScope.launch { container.preferences.setOverlayEnabled(value) }
    }

    fun setDictationMode(value: DictationMode) {
        viewModelScope.launch { container.preferences.setDictationMode(value) }
    }

    fun setNumberNormalizationEnabled(value: Boolean) {
        _numberNormalizationEnabled.value = value
        viewModelScope.launch { container.preferences.setNumberNormalizationEnabled(value) }
    }

    fun setNumberMinimumValue(value: Int) {
        _numberMinimumValue.value = value
        viewModelScope.launch { container.preferences.setNumberNormalizationMinimumValue(value) }
    }

    /**
     * Reads the Mac's own `/v1/status` and shows which of the three states it is in.
     * Never throws: an unreachable Mac is a result to display, not a crash to hide.
     */
    fun testMacConnection() {
        if (_macTest.value == MacTest.Testing) return
        _macTest.value = MacTest.Testing
        _macTestDetail.value = null
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) {
                MacEnginePlugin(
                    baseUrl = container.macBaseUrl().orEmpty(),
                    token = container.secrets.secret(EngineCatalog.ID_MAC).orEmpty(),
                ).status()
            }
            _macTestDetail.value = when (status) {
                is MacEnginePlugin.MacStatus.Reachable -> {
                    _macTest.value = MacTest.Reachable
                    status.detail
                }
                is MacEnginePlugin.MacStatus.ServerNotEnabled -> {
                    _macTest.value = MacTest.ServerNotEnabled
                    status.detail
                }
                is MacEnginePlugin.MacStatus.Unreachable -> {
                    _macTest.value = MacTest.Unreachable
                    status.detail
                }
            }
        }
    }

    private fun loadNormalizationSnapshot() {
        viewModelScope.launch {
            val settings = container.preferences.normalizationSettings()
            _numberNormalizationEnabled.update {
                TranscriptionNormalizationService.numberNormalizationEnabled(settings = settings)
            }
            _numberMinimumValue.update {
                TranscriptionNormalizationService.numberNormalizationMinimumValue(settings)
            }
        }
    }

    companion object {
        /** Tags the ported normalizer understands; the field still accepts anything BCP-47. */
        val SUPPORTED_LANGUAGES: List<String> = listOf("en", "de", "fr", "es", "nl", "zh", "ja")

        val NUMBER_MINIMUM_CHOICES: List<Int> = listOf(0, 10, 100)

        private val DEFAULT_MINIMUM: Int =
            TranscriptionNormalizationService.DEFAULT_NUMBER_NORMALIZATION_MINIMUM_VALUE

        private val SHARING = SharingStarted.WhileSubscribed(5_000)
    }
}
