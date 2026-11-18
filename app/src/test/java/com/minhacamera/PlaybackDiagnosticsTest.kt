package com.minhacamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDiagnosticsTest {
    @Test fun meterNeverPromisesAFrame() {
        val noHistory = PlaybackTiming.progress(3_000, null)
        assertEquals(20, noHistory.percentOfLimit)
        assertNull(noHistory.remainingEstimateSeconds)
        assertEquals(99, PlaybackTiming.progress(15_000, null).percentOfLimit)
        assertEquals(99, PlaybackTiming.progress(Long.MAX_VALUE, null).percentOfLimit)
        assertEquals(100, PlaybackTiming.progress(3_000, null, firstFrame = true).percentOfLimit)
    }

    @Test fun estimateIsSeparateAndCanBeExceeded() {
        val estimate = PlaybackTiming.estimate(listOf(4000, 4500, 9000, 5000, 4050))
        assertEquals(4500L, estimate)
        assertEquals(2, PlaybackTiming.progress(3000, estimate).remainingEstimateSeconds)
        val overdue = PlaybackTiming.progress(5000, estimate)
        assertEquals(33, overdue.percentOfLimit)
        assertTrue(overdue.overdue)
        assertNull(overdue.remainingEstimateSeconds)
        assertNull(PlaybackTiming.estimate(listOf(0, -1, 16_000L)))
        assertEquals(listOf(2L, 3L, 4L, 5L, 6L), PlaybackTiming.addSample(listOf(1L, 2L, 3L, 4L, 5L), 6L))
    }

    @Test fun journalIsBoundedAndMasksSecrets() {
        val journal = PlaybackJournal(2)
        journal.append(15, "abrindo")
        journal.append(600, "https://admin:supersecreto@camera.local/path?api_key=topsecret", failed = true)
        journal.append(700, "Authorization: Bearer sampletoken", failed = true)
        assertEquals(2, journal.entries().size)
        assertEquals(2, journal.failures)
        val report = journal.entries().joinToString(" ")
        assertTrue(report.contains("+600 ms · ERRO"))
        assertFalse(report.contains("supersecreto") || report.contains("topsecret") || report.contains("sampletoken"))
    }
}
