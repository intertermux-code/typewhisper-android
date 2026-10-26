package com.typewhisper.android.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typewhisper.android.app.engine.EngineCatalog
import com.typewhisper.android.app.engine.EngineDescriptor
import com.typewhisper.android.engines.whispercpp.WhisperModel
import com.typewhisper.android.storage.DictationMode
import com.typewhisper.android.storage.Profile

/**
 * Engine, credential, Mac harness, language, normalization, overlay and mic-input settings.
 *
 * Reads and writes go through [SettingsViewModel]; the screen itself holds only in-progress
 * text field content, and no credential value is ever shown after it has been saved.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val engineId by viewModel.engineId.collectAsStateWithLifecycle()
    val credentials by viewModel.hasCredential.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val macHost by viewModel.macHost.collectAsStateWithLifecycle()
    val macPort by viewModel.macPort.collectAsStateWithLifecycle()
    val macTest by viewModel.macTest.collectAsStateWithLifecycle()
    val macTestDetail by viewModel.macTestDetail.collectAsStateWithLifecycle()
    val overlayEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()
    val dictationMode by viewModel.dictationMode.collectAsStateWithLifecycle()
    val normalizationEnabled by viewModel.numberNormalizationEnabled.collectAsStateWithLifecycle()
    val numberMinimum by viewModel.numberMinimumValue.collectAsStateWithLifecycle()
    val micOptions by viewModel.micOptions.collectAsStateWithLifecycle()
    val micDeviceId by viewModel.micDeviceId.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val whisperModelId by viewModel.whisperModelId.collectAsStateWithLifecycle()
    val cellularDownload by viewModel.downloadOverCellular.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val customBaseUrl by viewModel.customBaseUrl.collectAsStateWithLifecycle()
    val missingSetup by viewModel.missingSetup.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Engine") {
            viewModel.engines.forEach { descriptor ->
                EngineRow(
                    descriptor = descriptor,
                    selected = descriptor.id == engineId,
                    missingSetup = missingSetup[descriptor.id],
                    onSelect = { viewModel.selectEngine(descriptor.id) },
                )
            }
        }

        SectionCard("On-device model") {
            ModelSection(
                models = viewModel.whisperModels,
                selectedId = whisperModelId,
                status = modelStatus,
                onSelect = viewModel::selectWhisperModel,
                onDownload = viewModel::downloadModel,
                allowCellular = cellularDownload,
                onAllowCellular = viewModel::setDownloadOverCellular,
            )
        }

        SectionCard("Keys") {
            Text(
                "An engine stays locked until its key is saved here. Saved values are never " +
                    "shown again; type a new one to replace them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Offered for every engine that takes a credential, not just the selected one:
            // saving the first key is what makes an engine selectable at all.
            viewModel.engines.filter { it.credentialLabel != null }.forEach { descriptor ->
                CredentialField(
                    engineId = descriptor.id,
                    label = "${descriptor.displayName} — ${descriptor.credentialLabel}",
                    saved = credentials[descriptor.id] == true,
                    onSave = viewModel::saveCredential,
                    onRemove = { viewModel.removeCredential(descriptor.id) },
                )
            }
        }

        SectionCard("Mac harness") {
            MacSection(
                host = macHost,
                port = macPort,
                test = macTest,
                testDetail = macTestDetail,
                onHostChange = viewModel::setMacHost,
                onPortChange = viewModel::setMacPort,
                onTest = viewModel::testMacConnection,
            )
        }

        SectionCard("Custom endpoint") {
            CustomEndpointSection(
                baseUrl = customBaseUrl,
                onChange = viewModel::setCustomBaseUrl,
            )
        }

        SectionCard("Spoken language") {
            LanguageField(
                language = language,
                onChange = viewModel::setLanguage,
            )
        }

        SectionCard("Profiles") {
            ProfilesSection(
                profiles = profiles,
                activeId = activeProfileId,
                engines = viewModel.engines,
                onApply = viewModel::applyProfile,
                onStopUsing = viewModel::stopUsingProfile,
                onCreate = viewModel::createProfile,
                onRename = viewModel::renameProfile,
                onDelete = viewModel::deleteProfile,
            )
        }

        SectionCard("Numbers") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.padding(end = 12.dp)) {
                    Text("Write spoken numbers as digits", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Turns \"twenty five\" into 25 once the value is at or above the limit below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = normalizationEnabled, onCheckedChange = viewModel::setNumberNormalizationEnabled)
            }
            Text("Normalize from", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsViewModel.NUMBER_MINIMUM_CHOICES.forEach { choice ->
                    if (choice == numberMinimum) {
                        Button(onClick = { viewModel.setNumberMinimumValue(choice) }) { Text("$choice") }
                    } else {
                        OutlinedButton(onClick = { viewModel.setNumberMinimumValue(choice) }) { Text("$choice") }
                    }
                }
            }
        }

        SectionCard("Microphone") {
            micOptions.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setMicDevice(option.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option.id == micDeviceId,
                        onClick = { viewModel.setMicDevice(option.id) },
                    )
                    Text(option.label, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(
                "If a Bluetooth mic is missing from this list, connect it and refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = viewModel::refreshMicrophones) { Text("Refresh list") }
        }

        SectionCard("Dictation mode") {
            DictationMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setDictationMode(mode) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = mode == dictationMode,
                        onClick = { viewModel.setDictationMode(mode) },
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "The Quick Settings tile is tap-only either way: Android hands a tile a click " +
                    "and no way to tell a press from a release.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Floating bubble") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Offer the overlay mic bubble", modifier = Modifier.padding(end = 12.dp))
                Switch(checked = overlayEnabled, onCheckedChange = viewModel::setOverlayEnabled)
            }
            Text(
                "The Quick Settings tile and the notification's Stop action dictate without it, " +
                    "so leaving this off costs nothing but the bubble.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EngineRow(
    descriptor: EngineDescriptor,
    selected: Boolean,
    missingSetup: String?,
    onSelect: () -> Unit,
) {
    val usable = missingSetup == null
    val contentColor = if (usable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = usable, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, enabled = usable, onClick = onSelect)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                if (descriptor.id == EngineCatalog.ID_WHISPERCPP) {
                    "${descriptor.displayName} (default)"
                } else {
                    descriptor.displayName
                },
                color = contentColor,
            )
            Text(
                descriptor.description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
            if (!usable) {
                Text(
                    "Needs $missingSetup",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CredentialField(
    engineId: String,
    label: String,
    saved: Boolean,
    onSave: (String, String) -> Unit,
    onRemove: () -> Unit,
) {
    // Keyed on the engine so a half-typed key cannot linger when the picker moves on.
    var value by rememberSaveable(engineId) { mutableStateOf("") }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = value.isNotBlank(),
            onClick = {
                onSave(engineId, value)
                value = ""
            },
        ) {
            Text(if (saved) "Replace" else "Save")
        }
        OutlinedButton(enabled = saved, onClick = onRemove) { Text("Remove") }
    }
    Text(
        if (saved) {
            "Saved in the phone's key store. The value is never shown again — type a new one to replace it."
        } else {
            "Stored in the phone's key store, not in the plain app preferences."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MacSection(
    host: String,
    port: Int,
    test: MacTest,
    testDetail: String?,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onTest: () -> Unit,
) {
    // Seeded once, then owned by the field: a persisted echo mid-typing would move the
    // cursor. An empty host is a real state — nothing is assumed about where the Mac is.
    var hostText by rememberSaveable { mutableStateOf(host) }
    var portText by rememberSaveable { mutableStateOf(port.toString()) }

    OutlinedTextField(
        value = hostText,
        onValueChange = {
            hostText = it
            onHostChange(it)
        },
        label = { Text("Relay host") },
        placeholder = { Text("Address of the machine running the relay") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = portText,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(5)
            portText = digits
            digits.toIntOrNull()?.let(onPortChange)
        },
        label = { Text("Relay port") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onTest, enabled = test != MacTest.Testing) { Text("Test connection") }
        if (test == MacTest.Testing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }

    val statusColor = when (test) {
        MacTest.Reachable -> MaterialTheme.colorScheme.primary
        MacTest.ServerNotEnabled, MacTest.Unreachable -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = when {
        hostText.isBlank() ->
            "No Mac set yet — type the address of the machine running the relay to unlock the engine."
        else -> when (test) {
            MacTest.Idle -> "Not tested yet."
            MacTest.Testing -> "Asking the Mac…"
            MacTest.Reachable -> "Reachable — TypeWhisper's server is answering."
            MacTest.ServerNotEnabled -> "Server not enabled — the relay is up, but TypeWhisper's HTTP server is off."
            MacTest.Unreachable -> "Unreachable — the Mac is asleep, off the network, or the relay isn't running."
        }
    }
    Text(statusLabel, color = if (hostText.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else statusColor)
    if (testDetail != null && test != MacTest.Idle && test != MacTest.Testing) {
        Text(
            testDetail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Text(
        MAC_SETUP_GUIDE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CustomEndpointSection(
    baseUrl: String,
    onChange: (String) -> Unit,
) {
    // Same rule as the relay host: seeded once, then the field owns it, including the
    // empty state that locks the engine again.
    var text by rememberSaveable { mutableStateOf(baseUrl) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text("Base URL") },
        placeholder = { Text("https://example.com/v1") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Nothing is filled in for you. Type the root of an OpenAI-compatible server and the " +
            "phone posts audio to <that>/audio/transcriptions, asking for " +
            "${EngineCatalog.CUSTOM_DEFAULT_MODEL}. Leave it empty and the engine stays locked.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LanguageField(
    language: String,
    onChange: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(language) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text("Language (BCP-47)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "auto lets the engine detect the language. " +
            "Known to the normalizer: ${SettingsViewModel.SUPPORTED_LANGUAGES.joinToString(", ")}.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProfilesSection(
    profiles: List<Profile>,
    activeId: String?,
    engines: List<EngineDescriptor>,
    onApply: (String) -> Unit,
    onStopUsing: () -> Unit,
    onCreate: (String, String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showingNew by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Profile?>(null) }

    Text(
        "A profile is a saved engine + language pair. Tap one to use it now; dictation " +
            "picks up the change on the next recording.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (profiles.isEmpty()) {
        Text("No profiles yet.")
    }
    profiles.forEach { profile ->
        val active = profile.id == activeId
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onApply(profile.id) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = active, onClick = { onApply(profile.id) })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    profileMeta(profile, engines),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { renaming = profile }) { Text("Rename") }
                TextButton(onClick = { onDelete(profile.id) }) { Text("Delete") }
            }
        }
    }
    if (activeId != null) {
        OutlinedButton(onClick = onStopUsing) { Text("Stop using a profile") }
    }
    Button(onClick = { showingNew = true }) { Text("Add profile") }

    if (showingNew) {
        NewProfileDialog(
            engines = engines,
            onDismiss = { showingNew = false },
            onCreate = { name, engineId, language ->
                onCreate(name, engineId, language)
                showingNew = false
            },
        )
    }
    renaming?.let { profile ->
        var name by remember(profile.id) { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename profile") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onRename(profile.id, name)
                        renaming = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NewProfileDialog(
    engines: List<EngineDescriptor>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var engineId by remember { mutableStateOf(engines.first().id) }
    var language by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                engines.forEach { descriptor ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { engineId = descriptor.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = descriptor.id == engineId, onClick = { engineId = descriptor.id })
                        Text(descriptor.displayName, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Language (blank = auto)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name, engineId, language) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun profileMeta(profile: Profile, engines: List<EngineDescriptor>): String {
    val engineName = engines.firstOrNull { it.id == profile.engineId }?.displayName ?: profile.engineId
    return "$engineName · ${profile.language ?: "auto"}"
}

@Composable
private fun ModelSection(
    models: List<WhisperModel>,
    selectedId: String,
    status: ModelStatus,
    onSelect: (String) -> Unit,
    onDownload: () -> Unit,
    allowCellular: Boolean,
    onAllowCellular: (Boolean) -> Unit,
) {
    Text(
        "The phone fetches the recogniser once. After that, on-device dictation works " +
            "with the radio off and no account anywhere.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    models.forEach { model ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(model.id) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = model.id == selectedId, onClick = { onSelect(model.id) })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(model.id, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${model.sizeBytes / (1024 * 1024)} MB on disk",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val downloading = status.fraction >= 0f
    when {
        status.downloaded && status.modelId == selectedId -> Text(
            "Downloaded and ready.",
            color = MaterialTheme.colorScheme.primary,
        )
        downloading -> {
            LinearProgressIndicator(
                progress = { status.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Downloading…", style = MaterialTheme.typography.bodySmall)
        }
        else -> {
            Button(onClick = onDownload, enabled = !downloading) {
                Text(if (status.modelId == selectedId) "Download now" else "Download $selectedId")
            }
        }
    }
    if (status.error != null) {
        Text(status.error, color = MaterialTheme.colorScheme.error)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Allow cellular downloads", modifier = Modifier.padding(end = 12.dp))
        Switch(checked = allowCellular, onCheckedChange = onAllowCellular)
    }
}

/** How each gesture is offered to the user, in the words the setting asks for. */
private val DictationMode.label: String
    get() = when (this) {
        DictationMode.TapToToggle -> "Tap to start/stop"
        DictationMode.PushToTalk -> "Push to talk"
    }

private val DictationMode.description: String
    get() = when (this) {
        DictationMode.TapToToggle -> "Tap the mic to start, tap again to stop and send."
        DictationMode.PushToTalk ->
            "Hold to record, let go to stop and send. A press shorter than a third of a " +
                "second is ignored."
    }

@Composable
internal fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

private val MAC_SETUP_GUIDE = """
On the Mac:
1. Open TypeWhisper and turn on its HTTP server in TypeWhisper's settings; the app has to keep running.
2. Start the relay on the Mac — python3 relay/tw_relay.py, or the LaunchAgent that loads at login. It listens on an address the phone can reach and forwards to TypeWhisper's local port.
3. Put that address in Relay host above, with port 18978.
4. Paste the token from ~/Library/Application Support/TypeWhisper/api-token into the Mac API token field, then press Test connection.

Reachable means dictation uses the Mac. Server not enabled is step 1. Unreachable is the Mac asleep, off the network, or the relay not running — either way the phone falls back to on-device transcription.
""".trimIndent()
