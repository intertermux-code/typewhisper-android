package com.typewhisper.android.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typewhisper.android.app.TypeWhisperApplication
import com.typewhisper.android.app.data.AppContainer
import com.typewhisper.android.storage.TranscriptionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the history list: newest dictations, or the ones matching the search field. */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val container: AppContainer = TypeWhisperApplication.container(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val records: StateFlow<List<TranscriptionRecord>> = _query
        .flatMapLatest { text ->
            // The repository is a suspend lookup, so the flow that needs it is built lazily
            // on IO instead of at construction time on the main thread.
            flow {
                val history = container.history()
                emitAll(if (text.isBlank()) history.recent() else history.search(text))
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun delete(id: String) {
        viewModelScope.launch { container.history().delete(id) }
    }
}
