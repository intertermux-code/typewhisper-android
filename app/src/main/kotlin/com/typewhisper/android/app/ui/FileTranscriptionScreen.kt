package com.typewhisper.android.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typewhisper.android.app.dictation.TranscriptPoster

/**
 * Transcription of an existing recording: pick the file, the engine from Settings does
 * the work, the text can be copied, shared, or pasted into the field the user is writing
 * in through the same nudge a dictation uses.
 */
@Composable
fun FileTranscriptionScreen(viewModel: FileTranscriptionViewModel) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val fileName by viewModel.fileName.collectAsStateWithLifecycle()
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::transcribe) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Transcribe a recording", style = MaterialTheme.typography.titleLarge)
        Text(
            "Pick any audio the phone can play — m4a, mp3, wav, flac, ogg. The engine you " +
                "picked in Settings does the work, so an on-device setup keeps the file on " +
                "the phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { pick.launch(arrayOf("audio/*")) },
            enabled = phase != FilePhase.Decoding && phase != FilePhase.Transcribing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (phase) {
                    FilePhase.Decoding -> "Reading the audio…"
                    FilePhase.Transcribing -> "Transcribing…"
                    else -> "Choose an audio file"
                },
            )
        }
        if (phase == FilePhase.Decoding || phase == FilePhase.Transcribing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                if (fileName != null) {
                    Text(
                        "  $fileName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val note = error
        if (note != null && phase != FilePhase.Done) {
            Text(note, color = MaterialTheme.colorScheme.error)
        }
        val text = transcript
        if (phase == FilePhase.Done && text != null) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    TranscriptPoster(context).copy(text)
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy")
                }
                OutlinedButton(onClick = { TranscriptPoster(context).deliver(text) }) {
                    Text("Paste into my field")
                }
                OutlinedButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(share, "Share transcript"))
                }) {
                    Text("Share")
                }
            }
        } else if (phase == FilePhase.Done) {
            Text("That file produced no text — it may be silence or music only.")
        }
    }
}
