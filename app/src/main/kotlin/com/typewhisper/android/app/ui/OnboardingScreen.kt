package com.typewhisper.android.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typewhisper.android.app.engine.OnDeviceModels
import com.typewhisper.android.app.engine.EngineCatalog
import com.typewhisper.android.engines.whispercpp.ModelDownloadException
import com.typewhisper.android.engines.whispercpp.WhisperModel
import com.typewhisper.android.ime.ImeSetup
import com.typewhisper.android.storage.PreferencesRepository
import kotlinx.coroutines.launch

/**
 * The setup a dictation app cannot skip and a user cannot guess: the microphone, the
 * notifications that report a failed transcript, the battery policy that would otherwise
 * kill a recording, the two ways to trigger a dictation, and the engine that writes it.
 *
 * Only the microphone is compulsory. The tile and the notification action work
 * without the overlay permission, the clipboard path works without the keyboard
 * being installed, and every engine but the on-device one is optional and unstated —
 * nothing here ever nags for an API key.
 *
 * Finishing writes `onboarding_complete` and the host screen switches on that value;
 * the flow owns no navigation of its own.
 */
@Composable
fun OnboardingScreen(preferences: PreferencesRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.WELCOME) }
    var micGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS),
        )
    }
    var batteryExempt by remember { mutableStateOf(isBatteryExempt(context)) }
    var keyboardActive by remember { mutableStateOf(ImeSetup.isActive(context)) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        if (granted) step = Step.NOTIFICATIONS
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("${step.ordinal + 1} of ${Step.COUNT}", style = MaterialTheme.typography.labelMedium)

        when (step) {
            Step.WELCOME -> Page(
                title = "Speak. TypeWhisper writes it.",
                body = "Dictate into any app — from a tile in the quick-settings shade, a bubble " +
                    "that floats over the keyboard you already use, or TypeWhisper's own voice " +
                    "keyboard. Recognition runs on this phone, so what you say stays on it.",
                primary = "Start setup",
                onPrimary = { step = Step.MICROPHONE },
            )

            Step.MICROPHONE -> Page(
                title = "Microphone",
                body = "Recording runs in a foreground service that shows a notification while it " +
                    "listens. Without the permission there is nothing to transcribe.",
                primary = if (micGranted) "Continue" else "Allow microphone",
                onPrimary = {
                    if (micGranted) step = Step.NOTIFICATIONS
                    else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                status = "granted".takeIf { micGranted },
            )

            Step.NOTIFICATIONS -> Page(
                title = "Notifications",
                body = "A dictation that fails — mic busy, model missing, Mac asleep — would fail " +
                    "in silence. The transcript and the reason for a failure both arrive as one.",
                primary = if (notificationsGranted) "Continue" else "Allow notifications",
                onPrimary = {
                    if (notificationsGranted) step = Step.BATTERY
                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                secondary = "Skip",
                onSecondary = { step = Step.BATTERY },
                status = "granted".takeIf { notificationsGranted },
            )

            Step.BATTERY -> Page(
                title = "Keep it running",
                body = "Xiaomi, Oppo, Vivo and OnePlus stop background services aggressively and cut " +
                    "a recording off halfway. Letting TypeWhisper ignore battery optimisation is " +
                    "what makes the tile dependable rather than occasional.",
                primary = if (batteryExempt) "Continue" else "Allow",
                onPrimary = {
                    if (batteryExempt) step = Step.BUBBLE
                    else context.startActivity(batteryIntent(context))
                },
                secondary = "Skip",
                onSecondary = { step = Step.BUBBLE },
                status = if (batteryExempt) "exempt" else "system dialog — come back here after allowing",
                onStatusClick = { batteryExempt = isBatteryExempt(context) },
            )

            Step.BUBBLE -> Page(
                title = "Bubble or tile",
                body = "The floating bubble sits over any app and starts a dictation on tap; a " +
                    "long-press on it jumps into the voice keyboard. It is the only step that " +
                    "needs permission to draw over other apps. Skip it and the quick-settings " +
                    "tile and the notification's Stop action start and stop dictation exactly " +
                    "the same way — add the tile from the shade whenever you like.",
                primary = "Allow overlay",
                onPrimary = { context.startActivity(overlayIntent(context)) },
                secondary = "Use the tile instead",
                onSecondary = { step = Step.KEYBOARD },
                status = "granted".takeIf { overlayGranted },
                onStatusClick = { overlayGranted = Settings.canDrawOverlays(context) },
            )

            Step.KEYBOARD -> Page(
                title = "Voice keyboard",
                body = "Optional. As your keyboard, TypeWhisper types the transcript into the field " +
                    "you are writing in. Without it the text goes to the clipboard with a " +
                    "tap-to-paste notification.",
                primary = "Open keyboard settings",
                onPrimary = { context.startActivity(ImeSetup.enableIntent()) },
                tertiary = if (keyboardActive) null else "Show the keyboard picker",
                onTertiary = { ImeSetup.showPicker(context) },
                secondary = "Skip",
                onSecondary = { step = Step.ENGINE },
                status = if (keyboardActive) "active" else "enable TypeWhisper, then select it",
                onStatusClick = { keyboardActive = ImeSetup.isActive(context) },
            )

            Step.ENGINE -> EnginePage(
                preferences = preferences,
                onContinue = { step = Step.DONE },
            )

            Step.DONE -> Page(
                title = "Ready",
                body = "Dictate from the tile, the bubble or the voice keyboard. Everything chosen " +
                    "here can be changed later in Settings.",
                primary = "Use TypeWhisper",
                onPrimary = {
                    scope.launch { preferences.setOnboardingComplete(true) }
                },
            )
        }
    }
}

/**
 * The engine choice and the one download it needs. On-device is default and complete in
 * this page; the Mac is explained but configured later in Settings; cloud engines are
 * mentioned once and never pushed.
 */
@Composable
private fun EnginePage(
    preferences: PreferencesRepository,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = WhisperModel.DEFAULT

    val engineId by preferences.engineId.collectAsStateWithLifecycle(PreferencesRepository.DEFAULT_ENGINE_ID)
    var fraction by remember { mutableStateOf(-1f) }
    var error by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf(OnDeviceModels.downloader(context).isDownloaded(model)) }
    var running by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Engine", style = MaterialTheme.typography.headlineSmall)
        EngineChoiceRow(
            label = EngineCatalog.default.displayName,
            detail = "Runs on this phone, offline. No account, no key.",
            selected = engineId == EngineCatalog.ID_WHISPERCPP,
            onSelect = { scope.launch { preferences.setEngineId(EngineCatalog.ID_WHISPERCPP) } },
        )
        EngineChoiceRow(
            label = "TypeWhisper on Mac",
            detail = "Uses the Mac app's engines over the network. Needs the relay, the Mac's " +
                "address and its API token — all laid out under Mac harness in Settings. " +
                "Until it is set up, the phone does the work itself.",
            selected = engineId == EngineCatalog.ID_MAC,
            onSelect = { scope.launch { preferences.setEngineId(EngineCatalog.ID_MAC) } },
        )
        Text(
            "Groq, OpenAI and your own gateway can be switched on in Settings once you " +
                "have a key for them. Setup finishes without any of that.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (engineId == EngineCatalog.ID_WHISPERCPP) {
            Text(
                "The recogniser is about ${model.sizeBytes / (1024 * 1024)} MB, fetched once " +
                    "over Wi-Fi. After that dictation works with the radio off.",
                style = MaterialTheme.typography.bodyMedium,
            )
            when {
                installed -> Text("Downloaded and ready.", color = MaterialTheme.colorScheme.primary)
                running -> {
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                    )
                    Text("Downloading…", style = MaterialTheme.typography.bodySmall)
                }
                else -> Button(
                    onClick = {
                        error = null
                        running = true
                        fraction = 0f
                        scope.launch {
                            try {
                                OnDeviceModels.ensureReady(context) { fraction = it }
                                installed = true
                            } catch (e: ModelDownloadException) {
                                error = OnDeviceModels.explain(context, e)
                            }
                            running = false
                            fraction = -1f
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Download the ${model.id} model")
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(if (installed || engineId != EngineCatalog.ID_WHISPERCPP) "Continue" else "Skip for now")
        }
    }
}

@Composable
private fun EngineChoiceRow(
    label: String,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Page(
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiary: String? = null,
    onTertiary: (() -> Unit)? = null,
    status: String? = null,
    onStatusClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(enabled = onStatusClick != null) { onStatusClick?.invoke() }
                    .padding(vertical = 4.dp),
            )
        }
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primary) }
        if (secondary != null && onSecondary != null) {
            OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondary) }
        }
        if (tertiary != null && onTertiary != null) {
            TextButton(onClick = onTertiary, modifier = Modifier.fillMaxWidth()) { Text(tertiary) }
        }
    }
}

private enum class Step {
    WELCOME, MICROPHONE, NOTIFICATIONS, BATTERY, BUBBLE, KEYBOARD, ENGINE, DONE,
    ;

    companion object {
        val COUNT: Int = entries.size
    }
}

private fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun overlayIntent(context: Context) =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

private fun isBatteryExempt(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
        ?.isIgnoringBatteryOptimizations(context.packageName) != false

/**
 * Asks the system to leave this app out of battery optimisation. The direct request
 * belongs here and nowhere else: a microphone service that dies two minutes into a
 * recording is a broken feature, not a power-management detail.
 */
private fun batteryIntent(context: Context) = Intent(
    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    Uri.parse("package:${context.packageName}"),
)
