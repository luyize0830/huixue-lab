package com.example.blehr.logic

import java.time.Instant
import java.time.ZoneId

/** Last night's recorded sessions, clipped to yesterday 18:00–today 12:00 and merged on overlap. */
object SleepWindow {
    data class Interval(val start: Instant, val end: Instant)

    fun range(now: Instant, zone: ZoneId): Interval {
        val today = now.atZone(zone).toLocalDate()
        val start = today.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val noon = today.atTime(12, 0).atZone(zone).toInstant()
        return Interval(start, minOf(now, noon))
    }

    fun minutes(sessions: List<Interval>, now: Instant, zone: ZoneId): Int? {
        val window = range(now, zone)
        val intervals = sessions.map { Interval(maxOf(it.start, window.start), minOf(it.end, window.end)) }
            .filter { it.end > it.start }.sortedBy { it.start }
        if (intervals.isEmpty()) return null
        var start = intervals.first().start
        var end = intervals.first().end
        var millis = 0L
        intervals.drop(1).forEach {
            if (it.start <= end) end = maxOf(end, it.end)
            else {
                millis += end.toEpochMilli() - start.toEpochMilli()
                start = it.start
                end = it.end
            }
        }
        millis += end.toEpochMilli() - start.toEpochMilli()
        return (millis / 60_000).toInt().takeIf { it > 0 }
    }
}
