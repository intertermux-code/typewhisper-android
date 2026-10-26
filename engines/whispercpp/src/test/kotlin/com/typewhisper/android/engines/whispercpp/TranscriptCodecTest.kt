package com.typewhisper.android.engines.whispercpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptCodecTest {

    @Test
    fun parsesSegmentsAndJoinedText() {
        val raw = "lang=en\n2\n0\t1500\tHello there\n1500\t3200\t how are you\n"
        val parsed = TranscriptCodec.parse(raw)
        assertFalse(parsed.aborted)
        assertEquals("en", parsed.detectedLanguage)
        assertEquals(2, parsed.segments.size)
        assertEquals(0L, parsed.segments[0].startMs)
        assertEquals(1500L, parsed.segments[0].endMs)
        assertEquals("Hello there", parsed.segments[0].text)
        // Native prefixes continuation segments with a space; the join keeps the
        // words apart and the final trim only removes the outer whitespace.
        assertEquals("Hello there how are you", parsed.text)
    }

    @Test
    fun detectsAbortedRun() {
        val raw = "lang=~?\n0\n"
        val parsed = TranscriptCodec.parse(raw)
        assertTrue(parsed.aborted)
        assertNull("unknown language during abort stays null", parsed.detectedLanguage)
        assertTrue(parsed.segments.isEmpty())
        assertEquals("", parsed.text)
    }

    @Test
    fun emptyPayloadIsSilentSuccess() {
        val parsed = TranscriptCodec.parse("")
        assertFalse(parsed.aborted)
        assertNull(parsed.detectedLanguage)
        assertEquals("", parsed.text)
        assertTrue(parsed.segments.isEmpty())
    }

    @Test
    fun unknownLanguageSentinelBecomesNull() {
        val parsed = TranscriptCodec.parse("lang=?\n1\n0\t10\t hi\n")
        assertFalse(parsed.aborted)
        assertNull(parsed.detectedLanguage)
        assertEquals("hi", parsed.text)
    }

    @Test
    fun countMismatchStopsAtAvailableLines() {
        // Header claims 3 segments but only 2 are present; parsing must not crash.
        val raw = "lang=de\n3\n0\t10\tGuten\n10\t20\tMorgen\n"
        val parsed = TranscriptCodec.parse(raw)
        assertEquals(2, parsed.segments.size)
        assertEquals("GutenMorgen", parsed.text)
    }
}
