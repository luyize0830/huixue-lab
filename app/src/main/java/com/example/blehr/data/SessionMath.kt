package com.example.blehr.data

/**
 * 把"这段手机是怎么用的"抽象成与 Android 无关的数据，好在 JVM 上做单元测试。
 *
 * 为什么单独抽出来：UsageStatsManager 的返回依赖真机行为（不同 ROM 事件类型、补点方式
 * 都不一样），但"怎么从一串事件算出连续时长/前台时长"是纯逻辑，必须能被测试覆盖。
 */
enum class UsageEventType { SCREEN_ON, SCREEN_OFF, RESUME, PAUSE }

data class UsageEvent(val type: UsageEventType, val ts: Long)

data class UsageResult(
    val sessionMinutes: Int,
    val foregroundMinutesLastHour: Int
)

object SessionMath {

    /** 停用超过这个间隔就算这一口气断了 */
    const val SESSION_GAP_MS = 5 * 60 * 1000L

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS

    /**
     * @param events 按时间升序排列的事件
     * @param now 当前时间
     */
    fun compute(events: List<UsageEvent>, now: Long): UsageResult {
        val hourStart = now - HOUR_MS
        var sessionStart = 0L
        var lastEventTs = 0L
        var fgStart = 0L
        var fgInLastHour = 0L
        var lastType: UsageEventType? = null

        for (e in events) {
            val ts = e.ts.coerceAtMost(now)
            lastType = e.type
            when (e.type) {
                UsageEventType.SCREEN_ON -> {
                    if (sessionStart == 0L || ts - lastEventTs > SESSION_GAP_MS) sessionStart = ts
                    lastEventTs = ts
                }

                UsageEventType.SCREEN_OFF -> {
                    sessionStart = 0L
                    lastEventTs = ts
                    if (fgStart > 0L) {
                        fgInLastHour += overlap(fgStart, ts, hourStart, now)
                        fgStart = 0L
                    }
                }

                UsageEventType.RESUME -> {
                    if (sessionStart == 0L || ts - lastEventTs > SESSION_GAP_MS) sessionStart = ts
                    if (fgStart == 0L) fgStart = ts
                    lastEventTs = ts
                }

                UsageEventType.PAUSE -> {
                    if (fgStart > 0L) {
                        fgInLastHour += overlap(fgStart, ts, hourStart, now)
                        fgStart = 0L
                    }
                    lastEventTs = ts
                }
            }
        }

        // 还没结束的这段前台时间要算到"现在"
        if (fgStart > 0L) fgInLastHour += overlap(fgStart, now, hourStart, now)

        // 最后一步是"离开"，而且离开已经超过 5 分钟：说明这一口气其实已经断了。
        // （常见情况：用户切到别的 app 就没再回来，系统没补熄屏事件。）
        if (lastType == UsageEventType.PAUSE && now - lastEventTs > SESSION_GAP_MS) {
            sessionStart = 0L
        }

        val sessionMinutes = if (sessionStart > 0L) {
            ((now - sessionStart) / MINUTE_MS).toInt().coerceIn(0, 8 * 60)
        } else 0

        return UsageResult(
            sessionMinutes = sessionMinutes,
            foregroundMinutesLastHour = (fgInLastHour / MINUTE_MS).toInt().coerceIn(0, 60)
        )
    }

    private fun overlap(start: Long, end: Long, winStart: Long, winEnd: Long): Long {
        val s = maxOf(start, winStart)
        val e = minOf(end, winEnd)
        return if (e > s) e - s else 0L
    }
}