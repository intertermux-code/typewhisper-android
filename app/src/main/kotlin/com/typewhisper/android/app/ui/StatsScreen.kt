package com.typewhisper.android.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Two weeks of dictation, one row per day. Counts and minutes only — the words stay in
 * History, where the user put them there deliberately.
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Usage", style = MaterialTheme.typography.titleLarge)
        Text(
            "Last ${StatsViewModel.DAYS_WINDOW} days, from the dictations in History.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("Dictations", "${viewModel.totalDictations}")
                Stat("Minutes", "${viewModel.totalMinutes}")
                Stat("Days used", "${days.count { it.dictations > 0 }}")
            }
        }
        if (loaded && days.isEmpty()) {
            Text(
                "Nothing yet. Dictate once and the numbers start here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        days.forEach { day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(day.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                Text(
                    "${day.dictations} · ${day.totalSeconds}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
        OutlinedButton(onClick = viewModel::refresh) { Text("Refresh") }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
