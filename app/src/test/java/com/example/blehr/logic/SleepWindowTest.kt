package com.example.blehr.logic

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SleepWindowTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-11-18T06:00:00Z") // 14:00 local
    private fun interval(start: String, end: String) = SleepWindow.Interval(Instant.parse(start), Instant.parse(end))

    @Test fun previousLongNightCannotReplaceLastNightsShortSleep() {
        val sessions = listOf(interval("2026-11-16T14:00:00Z", "2026-11-17T00:00:00Z"),
            interval("2026-11-17T20:00:00Z", "2026-11-18T00:00:00Z"))
        assertEquals(240, SleepWindow.minutes(sessions, now, zone))
    }
    @Test fun splitSessionsAccumulateWithoutDoubleCountingOverlappingSources() {
        val sessions = listOf(interval("2026-11-17T15:00:00Z", "2026-11-17T18:00:00Z"),
            interval("2026-11-17T15:00:00Z", "2026-11-17T18:00:00Z"),
            interval("2026-11-17T19:00:00Z", "2026-11-18T00:00:00Z"))
        assertEquals(480, SleepWindow.minutes(sessions, now, zone))
    }
    @Test fun missingDataAndAfternoonNapsAreUnknown() {
        assertNull(SleepWindow.minutes(emptyList(), now, zone))
        assertNull(SleepWindow.minutes(listOf(interval("2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")), now, zone))
    }
    @Test fun futurePortionIsClippedAndInvalidIntervalsAreIgnored() {
        val morning = Instant.parse("2026-11-17T22:00:00Z")
        assertEquals(420, SleepWindow.minutes(listOf(
            interval("2026-11-17T15:00:00Z", "2026-11-18T00:00:00Z"),
            interval("2026-11-18T00:00:00Z", "2026-11-17T15:00:00Z")), morning, zone))
    }
}
