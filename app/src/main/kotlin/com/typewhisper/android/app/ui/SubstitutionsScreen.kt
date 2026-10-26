package com.typewhisper.android.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Dictionary and snippet CRUD, the phrases the user teaches the app. */
@Composable
fun SubstitutionsScreen(viewModel: SubstitutionsViewModel) {
    val dictionary by viewModel.dictionary.collectAsStateWithLifecycle()
    val snippets by viewModel.snippets.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Dictionary") {
            if (dictionary.isEmpty()) {
                Text(
                    "No entries yet. Each one rewrites a phrase the engine mishears.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            dictionary.forEach { entry ->
                EntryLine(
                    left = entry.spoken,
                    right = entry.written,
                    onDelete = { viewModel.deleteDictionaryEntry(entry) },
                )
            }
            AddEntryFields(
                firstLabel = "Spoken",
                secondLabel = "Written",
                onAdd = viewModel::addDictionaryEntry,
            )
        }

        SectionCard("Snippets") {
            if (snippets.isEmpty()) {
                Text(
                    "No snippets yet. Typing a snippet's trigger in a dictation expands it into the full text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            snippets.forEach { snippet ->
                EntryLine(
                    left = snippet.trigger,
                    right = snippet.expansion,
                    onDelete = { viewModel.deleteSnippet(snippet) },
                )
            }
            AddEntryFields(
                firstLabel = "Trigger",
                secondLabel = "Expansion",
                onAdd = viewModel::addSnippet,
            )
        }
    }
}

@Composable
private fun EntryLine(left: String, right: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$left  →  $right",
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        OutlinedButton(onClick = onDelete) { Text("Delete") }
    }
}

@Composable
private fun AddEntryFields(
    firstLabel: String,
    secondLabel: String,
    onAdd: (String, String) -> Unit,
) {
    var first by rememberSaveable(firstLabel) { mutableStateOf("") }
    var second by rememberSaveable(secondLabel) { mutableStateOf("") }
    OutlinedTextField(
        value = first,
        onValueChange = { first = it },
        label = { Text(firstLabel) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = second,
        onValueChange = { second = it },
        label = { Text(secondLabel) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        enabled = first.isNotBlank() && second.isNotBlank(),
        onClick = {
            onAdd(first, second)
            first = ""
            second = ""
        },
    ) {
        Text("Add")
    }
}
