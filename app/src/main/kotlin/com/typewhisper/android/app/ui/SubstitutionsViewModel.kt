package com.typewhisper.android.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typewhisper.android.app.TypeWhisperApplication
import com.typewhisper.android.app.data.AppContainer
import com.typewhisper.android.core.PostProcessing
import com.typewhisper.android.storage.DictionaryEntry
import com.typewhisper.android.storage.Snippet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Dictionary and snippet rows, the phrases the user wants written differently and the
 * shortcuts that expand into longer text. Both are applied to a transcript by
 * [TranscriptPostProcessor][com.typewhisper.android.app.dictation.TranscriptPostProcessor]
 * before it reaches the clipboard.
 */
class SubstitutionsViewModel(application: Application) : AndroidViewModel(application) {

    private val container: AppContainer = TypeWhisperApplication.container(application)

    val dictionary: StateFlow<List<PostProcessing.DictionaryEntry>> = flow {
        emitAll(container.substitutions().dictionary)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SHARING, emptyList())

    val snippets: StateFlow<List<PostProcessing.Snippet>> = flow {
        emitAll(container.substitutions().snippets)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SHARING, emptyList())

    fun addDictionaryEntry(spoken: String, written: String) {
        if (spoken.isBlank() || written.isBlank()) return
        viewModelScope.launch {
            container.substitutions().save(DictionaryEntry(spoken.trim(), written.trim()))
        }
    }

    fun deleteDictionaryEntry(entry: PostProcessing.DictionaryEntry) {
        viewModelScope.launch {
            container.substitutions().delete(DictionaryEntry(entry.spoken, entry.written))
        }
    }

    fun addSnippet(trigger: String, expansion: String) {
        if (trigger.isBlank() || expansion.isBlank()) return
        viewModelScope.launch {
            container.substitutions().save(Snippet(trigger.trim(), expansion.trim()))
        }
    }

    fun deleteSnippet(snippet: PostProcessing.Snippet) {
        viewModelScope.launch {
            container.substitutions().delete(Snippet(snippet.trigger, snippet.expansion))
        }
    }

    private companion object {
        private val SHARING = SharingStarted.WhileSubscribed(5_000)
    }
}
