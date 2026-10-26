package com.typewhisper.android.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typewhisper.android.app.TypeWhisperApplication
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One calendar day's use: how often the user dictated and for how long. */
data class UsageDay(val day: LocalDate, val dictations: Int, val totalSeconds: Long)

/** Drives the statistics screen — deliberately modest, it sums what history already holds. */
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = TypeWhisperApplication.container(application)

    private val _days = MutableStateFlow<List<UsageDay>>(emptyList())
    val days: StateFlow<List<UsageDay>> = _days.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val totalDictations: Int get() = _days.value.sumOf { it.dictations }
    val totalMinutes: Long get() = _days.value.sumOf { it.totalSeconds } / 60

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val since = Instant.now().minus(DAYS_WINDOW, java.time.temporal.ChronoUnit.DAYS)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val records = container.history().since(since.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
            _days.value = records
                .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() }
                .map { (day, sameDay) ->
                    UsageDay(
                        day = day,
                        dictations = sameDay.size,
                        totalSeconds = sameDay.sumOf { it.durationMs } / 1000,
                    )
                }
                .sortedByDescending { it.day }
            _loaded.value = true
        }
    }

    companion object {
        const val DAYS_WINDOW: Long = 14
    }
}
