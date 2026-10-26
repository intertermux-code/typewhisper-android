package com.typewhisper.android.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PostProcessingTest {

    private val dictionary = listOf(
        PostProcessing.DictionaryEntry("hyper tunnel", "hypertunnel"),
        PostProcessing.DictionaryEntry("hyper", "Hyper"),
        PostProcessing.DictionaryEntry("api", "API"),
        PostProcessing.DictionaryEntry("san francisco", "San Francisco"),
    )

    private val snippets = listOf(
        PostProcessing.Snippet("eml", "example@example.com"),
        PostProcessing.Snippet("addr", "1 Infinite Loop, Cupertino"),
    )

    @Test
    fun dictionaryReplacesWholePhrasesCaseInsensitively() {
        assertEquals(
            "the hypertunnel is ready",
            PostProcessing.applyDictionary("the hyper Tunnel is ready", dictionary),
        )
        assertEquals("the API and API", PostProcessing.applyDictionary("the API and api", dictionary))
    }

    @Test
    fun dictionaryRespectsWordBoundaries() {
        assertEquals("apocryphal", PostProcessing.applyDictionary("apocryphal", dictionary))
        assertEquals("apiary and the API", PostProcessing.applyDictionary("apiary and the api", dictionary))
    }

    @Test
    fun longerEntriesWin() {
        assertEquals("the hypertunnel", PostProcessing.applyDictionary("the hyper tunnel", dictionary))
        assertEquals("see San Francisco today", PostProcessing.applyDictionary("see san francisco today", dictionary))
    }

    @Test
    fun replacementTextIsNotRescanned() {
        val chained = listOf(
            PostProcessing.DictionaryEntry("one", "two"),
            PostProcessing.DictionaryEntry("two", "three"),
        )
        assertEquals("two", PostProcessing.applyDictionary("one", chained))
    }

    @Test
    fun snippetsExpandTheirTrigger() {
        assertEquals(
            "mail example@example.com please",
            PostProcessing.expandSnippets("mail eml please", snippets),
        )
        assertEquals(
            "mail emlx keeps the trigger",
            PostProcessing.expandSnippets("mail emlx keeps the trigger", snippets),
        )
    }

    @Test
    fun identityAndEmptyEntriesLeaveTextUnchanged() {
        val text = "nothing to change"
        assertEquals(text, PostProcessing.applyDictionary(text, listOf(PostProcessing.DictionaryEntry("change", "change"))))
        assertEquals(text, PostProcessing.applyDictionary(text, emptyList()))
        assertEquals(text, PostProcessing.expandSnippets(text, emptyList()))
    }
}
