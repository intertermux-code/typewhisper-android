package com.typewhisper.android.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typewhisper.android.app.dictation.DictationControl
import com.typewhisper.android.app.dictation.bubble.BubbleOverlayService
import com.typewhisper.android.app.ui.CompanionControlsScreen
import com.typewhisper.android.app.ui.FileTranscriptionScreen
import com.typewhisper.android.app.ui.FileTranscriptionViewModel
import com.typewhisper.android.app.ui.HistoryScreen
import com.typewhisper.android.app.ui.HistoryViewModel
import com.typewhisper.android.app.ui.OnboardingScreen
import com.typewhisper.android.app.ui.SettingsScreen
import com.typewhisper.android.app.ui.SettingsViewModel
import com.typewhisper.android.app.ui.StatsScreen
import com.typewhisper.android.app.ui.StatsViewModel
import com.typewhisper.android.app.ui.SubstitutionsScreen
import com.typewhisper.android.app.ui.SubstitutionsViewModel
import com.typewhisper.android.app.ui.theme.TypeWhisperTheme
import com.typewhisper.android.ime.ImeSetup

/**
 * Runs the first-run onboarding, then hosts the companion-mode controls, settings,
 * file transcription, history, usage stats and substitutions. Owns the runtime
 * permission prompts the background triggers cannot show themselves.
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** Set by the paste nudge's "Dictate again" action. */
        const val EXTRA_AUTO_START_DICTATION = "auto_start_dictation"

        private val TAB_TITLES = listOf("Dictation", "Settings", "Audio file", "History", "Usage", "Phrases")
    }

    private val settingsViewModel: SettingsViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val substitutionsViewModel: SubstitutionsViewModel by viewModels()
    private val fileViewModel: FileTranscriptionViewModel by viewModels()
    private val statsViewModel: StatsViewModel by viewModels()

    private var hasAudioPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(true)
    private var overlayGranted by mutableStateOf(false)
    private var bubbleVisible by mutableStateOf(false)
    private var imeEnabled by mutableStateOf(false)
    private var imeActive by mutableStateOf(false)

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasAudioPermission = granted }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasNotificationPermission = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissions()
        setContent {
            TypeWhisperTheme {
                // Without a Surface Compose draws black text on the window's dark grey and
                // the scheme's background/onBackground never reach the screen.
                Surface(modifier = Modifier.fillMaxSize()) {
                    val onboarding by settingsViewModel.onboardingComplete.collectAsStateWithLifecycle()
                    // Setup owns the first run completely; the tabs are the post-onboarding app.
                    // While DataStore has not answered, draw nothing rather than guess.
                    when (onboarding) {
                        null -> Unit
                        false -> OnboardingScreen(TypeWhisperApplication.container(this).preferences)
                        else -> Tabs()
                    }
                }
            }
        }
        handleAutoStart(intent = intent)
    }

    @Composable
    private fun Tabs() {
        var selectedTab by remember { mutableIntStateOf(0) }
        // Each screen scrolls itself: an outer scroll container here would fight the
        // history list's LazyColumn over nested scrolling.
        Column {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                TAB_TITLES.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                    ) {
                        Text(title, modifier = Modifier.padding(12.dp))
                    }
                }
            }
            when (selectedTab) {
                0 -> CompanionControlsScreen(
                    hasAudioPermission = hasAudioPermission,
                    hasNotificationPermission = hasNotificationPermission,
                    overlayGranted = overlayGranted,
                    bubbleVisible = bubbleVisible,
                    imeEnabled = imeEnabled,
                    imeActive = imeActive,
                    onRequestAudioPermission = {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRequestNotificationPermission = {
                        requestNotificationPermission()
                    },
                    onOpenOverlaySettings = { openOverlaySettings() },
                    onToggleBubble = { toggleBubble() },
                    onShowImePicker = { ImeSetup.showPicker(this@MainActivity) },
                    onOpenImeSettings = { startActivity(ImeSetup.enableIntent()) },
                )
                1 -> SettingsScreen(settingsViewModel)
                2 -> FileTranscriptionScreen(fileViewModel)
                3 -> HistoryScreen(historyViewModel)
                4 -> StatsScreen(statsViewModel)
                else -> SubstitutionsScreen(substitutionsViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read after returning from the overlay-settings screen.
        refreshPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutoStart(intent)
    }

    private fun handleAutoStart(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTO_START_DICTATION, false) != true) return
        intent?.removeExtra(EXTRA_AUTO_START_DICTATION)
        if (hasAudioPermission) DictationControl.start(this)
        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun refreshPermissions() {
        hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        overlayGranted = BubbleOverlayService.canShowOverlays(this)
        imeEnabled = ImeSetup.isEnabled(this)
        imeActive = ImeSetup.isActive(this)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun toggleBubble() {
        if (!overlayGranted) {
            openOverlaySettings()
            return
        }
        if (bubbleVisible) {
            BubbleOverlayService.stop(this)
            bubbleVisible = false
        } else {
            BubbleOverlayService.start(this)
            bubbleVisible = true
        }
    }
}
