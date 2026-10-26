package com.typewhisper.android.engines.whispercpp

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperLanguagesTest {

    @Test
    fun mapsSupportedSubtags() {
        assertEquals("en", WhisperLanguages.toWhisperLanguage("en"))
        assertEquals("de", WhisperLanguages.toWhisperLanguage("de"))
        assertEquals("fr", WhisperLanguages.toWhisperLanguage("fr"))
        assertEquals("es", WhisperLanguages.toWhisperLanguage("es"))
        assertEquals("nl", WhisperLanguages.toWhisperLanguage("nl"))
        assertEquals("zh", WhisperLanguages.toWhisperLanguage("zh"))
        assertEquals("ja", WhisperLanguages.toWhisperLanguage("ja"))
    }

    @Test
    fun reducesRegionAndScriptSubtags() {
        assertEquals("en", WhisperLanguages.toWhisperLanguage("en-US"))
        assertEquals("de", WhisperLanguages.toWhisperLanguage("de-DE"))
        assertEquals("zh", WhisperLanguages.toWhisperLanguage("zh-Hans-CN"))
        assertEquals("fr", WhisperLanguages.toWhisperLanguage("fr_fr"))
    }

    @Test
    fun isCaseInsensitive() {
        assertEquals("en", WhisperLanguages.toWhisperLanguage("EN"))
        assertEquals("ja", WhisperLanguages.toWhisperLanguage("ja-JP"))
    }

    @Test
    fun nullBlankAutoAndUnknownFallBackToAuto() {
        assertEquals("auto", WhisperLanguages.toWhisperLanguage(null))
        assertEquals("auto", WhisperLanguages.toWhisperLanguage(""))
        assertEquals("auto", WhisperLanguages.toWhisperLanguage("  "))
        assertEquals("auto", WhisperLanguages.toWhisperLanguage("auto"))
        // A supported language we do not gate on is treated as auto-detect.
        assertEquals("auto", WhisperLanguages.toWhisperLanguage("pt"))
        assertEquals("auto", WhisperLanguages.toWhisperLanguage("!!"))
    }

    @Test
    fun primarySubtagHandlesSeparators() {
        assertEquals("es", WhisperLanguages.primarySubtag("es-419"))
        assertEquals("nl", WhisperLanguages.primarySubtag("nl_Latn"))
        assertEquals(null, WhisperLanguages.primarySubtag(null))
        assertEquals(null, WhisperLanguages.primarySubtag("   "))
    }
}
