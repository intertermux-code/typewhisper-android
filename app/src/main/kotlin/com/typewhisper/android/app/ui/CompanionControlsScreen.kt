package com.typewhisper.android.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typewhisper.android.app.dictation.DictationControl
import com.typewhisper.android.app.dictation.DictationPhase

/**
 * Companion-mode control surface: the in-app dictation button, the permission state the
 * background triggers depend on, and the one choice that decides how dictated text
 * reaches the field — through the voice keyboard or through the clipboard.
 */
@Composable
fun CompanionControlsScreen(
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    overlayGranted: Boolean,
    bubbleVisible: Boolean,
    imeEnabled: Boolean,
    imeActive: Boolean,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onToggleBubble: () -> Unit,
    onShowImePicker: () -> Unit,
    onOpenImeSettings: () -> Unit,
) {
    val phase by DictationControl.phase.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Companion dictation", style = MaterialTheme.typography.titleLarge)
        Text(
            "Dictate up to ten seconds at a go, and the words land in the field you are " +
                "writing in. This button is always tap to start and tap to stop; the bubble " +
                "and the voice keyboard follow the dictation mode in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = {
                if (!hasAudioPermission) {
                    onRequestAudioPermission()
                } else if (!hasNotificationPermission) {
                    onRequestNotificationPermission()
                } else {
                    DictationControl.toggle(context)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (phase) {
                    DictationPhase.Recording -> "Stop dictation"
                    DictationPhase.Finishing -> "Stopping…"
                    DictationPhase.Idle -> "Start dictation"
                },
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Where text lands", style = MaterialTheme.typography.titleSmall)
                when {
                    imeActive -> Text(
                        "The voice keyboard is your keyboard right now — dictated text is typed " +
                            "straight into the focused field.",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    imeEnabled -> {
                        Text(
                            "The voice keyboard is enabled but another keyboard is active. " +
                                "Switch to it and dictated text types into the field directly; " +
                                "the globe key takes you back.",
                        )
                        Button(onClick = onShowImePicker, modifier = Modifier.fillMaxWidth()) {
                            Text("Switch to the voice keyboard")
                        }
                    }
                    else -> {
                        Text(
                            "Until the voice keyboard is switched on, transcripts go to the " +
                                "clipboard with a tap-to-paste notification. The keyboard makes " +
                                "insertion direct.",
                        )
                        OutlinedButton(onClick = onOpenImeSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("Set up the voice keyboard")
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Permissions", style = MaterialTheme.typography.titleSmall)
                PermissionRow("Microphone", hasAudioPermission)
                PermissionRow("Notifications", hasNotificationPermission)
                PermissionRow("Display over other apps", overlayGranted)

                if (!overlayGranted) {
                    OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Grant overlay access")
                    }
                } else {
                    OutlinedButton(onClick = onToggleBubble, modifier = Modifier.fillMaxWidth()) {
                        Text(if (bubbleVisible) "Hide bubble" else "Show bubble")
                    }
                }
                Text(
                    "The Quick Settings tile and the notification's Stop action work without " +
                        "overlay permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(if (granted) "Granted" else "Not granted")
    }
}
