package com.typewhisper.android.app.ui

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typewhisper.android.app.dictation.TranscriptPoster
import com.typewhisper.android.storage.TranscriptionRecord

/**
 * Everything the phone has dictated: newest first, searchable, and each row can be copied,
 * put back on the clipboard with the paste nudge, or dropped.
 *
 * "Insert again" is deliberately [TranscriptPoster]'s delivery path rather than a bare
 * clipboard write — the notification is how companion mode gets text into another app, so
 * replaying a dictation has to behave exactly like making one.
 */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = { Text("Search dictations") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        if (records.isEmpty()) {
            Text(
                if (query.isBlank()) {
                    "No dictations yet. Start one from the tile, the bubble, or the Dictation tab."
                } else {
                    "Nothing matches \"$query\"."
                },
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(records, key = { it.id }) { record ->
                HistoryRow(
                    record = record,
                    onCopy = {
                        TranscriptPoster(context).copy(record.text)
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    onInsertAgain = { TranscriptPoster(context).deliver(record.text) },
                    onDelete = { viewModel.delete(record.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HistoryRow(
    record: TranscriptionRecord,
    onCopy: () -> Unit,
    onInsertAgain: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(record.text, maxLines = 5, overflow = TextOverflow.Ellipsis)
        Text(
            metaLine(record),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCopy) { Text("Copy") }
            OutlinedButton(onClick = onInsertAgain) { Text("Insert again") }
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

private fun metaLine(record: TranscriptionRecord): String {
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        record.createdAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    val seconds = record.durationMs / 1000
    val language = record.language?.takeIf { it.isNotBlank() && it != "auto" }
    return listOfNotNull(
        record.engineId,
        language,
        "${seconds}s",
        relativeTime.toString(),
    ).joinToString(" · ")
}
