package com.typewhisper.android.engines.whispercpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {

    @Test
    fun requiredFreeBytesAddsMargin() {
        val size = 32_152_673L
        assertEquals(size + DownloadPolicy.MIN_FREE_MARGIN_BYTES, DownloadPolicy.requiredFreeBytes(size))
    }

    @Test
    fun freeSpaceDecision() {
        val size = 1000L
        val needed = DownloadPolicy.requiredFreeBytes(size)
        assertTrue(DownloadPolicy.hasEnoughFreeSpace(needed, size))
        assertTrue(DownloadPolicy.hasEnoughFreeSpace(needed + 1, size))
        assertFalse(DownloadPolicy.hasEnoughFreeSpace(needed - 1, size))
        assertFalse("unknown free space must not be guessed at", DownloadPolicy.hasEnoughFreeSpace(-1, size))
    }

    @Test
    fun progressFractionClampsAndGuards() {
        assertEquals(0.5f, DownloadPolicy.progressFraction(50, 100))
        assertEquals(0f, DownloadPolicy.progressFraction(0, 100))
        assertEquals("unknown total keeps the bar at the start", 0f, DownloadPolicy.progressFraction(50, 0))
        assertEquals("negative total", 0f, DownloadPolicy.progressFraction(50, -10))
        assertEquals("overshoot clamps", 1f, DownloadPolicy.progressFraction(150, 100))
        assertEquals("negative progress", 0f, DownloadPolicy.progressFraction(-5, 100))
    }

    @Test
    fun resumeEligibility() {
        assertTrue(DownloadPolicy.shouldResume(existingBytes = 500, totalBytes = 1000, serverAcceptsRanges = true))
        assertFalse("no partial to resume", DownloadPolicy.shouldResume(0, 1000, true))
        assertFalse("already complete", DownloadPolicy.shouldResume(1000, 1000, true))
        assertFalse("oversized partial is corrupt", DownloadPolicy.shouldResume(1500, 1000, true))
        assertFalse("server without range support", DownloadPolicy.shouldResume(500, 1000, false))
    }

    @Test
    fun meteredGate() {
        assertTrue(DownloadPolicy.shouldDownload(isMetered = false, userOptedInMetered = false))
        assertTrue(DownloadPolicy.shouldDownload(isMetered = true, userOptedInMetered = true))
        assertFalse(DownloadPolicy.shouldDownload(isMetered = true, userOptedInMetered = false))
    }
}
