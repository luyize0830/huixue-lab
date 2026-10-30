package com.example.blehr.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SessionMath] 的口径测试。
 *
 * 这些数字就是"产品怎么理解你的手机使用"的定义，改动规则必须同步改测试，
 * 所以这里每个用例都写清楚"为什么应该是这个数"。
 */
class SessionMathTest {

    private val now = 1_700_000_000_000L
    private val min = 60_000L

    private fun ev(type: UsageEventType, minutesAgo: Long) =
        UsageEvent(type, now - minutesAgo * min)

    @Test
    fun `没有事件时一切为零`() {
        val r = SessionMath.compute(emptyList(), now)
        assertEquals(0, r.sessionMinutes)
        assertEquals(0, r.foregroundMinutesLastHour)
    }

    @Test
    fun `亮屏后一直没熄屏 就一直在计时`() {
        val r = SessionMath.compute(listOf(ev(UsageEventType.SCREEN_ON, 30)), now)
        assertEquals(30, r.sessionMinutes)
        assertEquals(0, r.foregroundMinutesLastHour)
    }

    @Test
    fun `熄屏之后连续使用归零`() {
        val events = listOf(
            ev(UsageEventType.SCREEN_ON, 60),
            ev(UsageEventType.SCREEN_OFF, 10)
        )
        val r = SessionMath.compute(events, now)
        assertEquals(0, r.sessionMinutes)
        assertEquals(0, r.foregroundMinutesLastHour)
    }

    @Test
    fun `离开 10 分钟后 连续时长归零但前台时长仍然计入`() {
        val events = listOf(
            ev(UsageEventType.RESUME, 30),
            ev(UsageEventType.PAUSE, 10)
        )
        val r = SessionMath.compute(events, now)
        // 30 分钟前开始用、10 分钟前放下：现在并没有在"连续使用"
        assertEquals(0, r.sessionMinutes)
        // 但那一小时里确实有 20 分钟是有 app 在前台的
        assertEquals(20, r.foregroundMinutesLastHour)
    }

    @Test
    fun `离开超过五分钟 就不算还在连续使用`() {
        val events = listOf(
            ev(UsageEventType.RESUME, 90),
            ev(UsageEventType.PAUSE, 80)
        )
        val r = SessionMath.compute(events, now)
        assertEquals(0, r.sessionMinutes)
        assertEquals(0, r.foregroundMinutesLastHour)
    }

    @Test
    fun `前台时段跨过一小时边界 只算落在窗口里的部分`() {
        val events = listOf(
            ev(UsageEventType.RESUME, 90),
            ev(UsageEventType.PAUSE, 30)
        )
        val r = SessionMath.compute(events, now)
        assertEquals(30, r.foregroundMinutesLastHour)
        assertEquals(0, r.sessionMinutes)
    }

    @Test
    fun `一小时内多段前台会累加`() {
        val events = listOf(
            ev(UsageEventType.RESUME, 50),
            ev(UsageEventType.PAUSE, 45),
            ev(UsageEventType.RESUME, 20),
            ev(UsageEventType.PAUSE, 2)
        )
        val r = SessionMath.compute(events, now)
        assertEquals(5 + 18, r.foregroundMinutesLastHour)
        // 中间离开了 25 分钟（> 5 分钟），所以这算新的一口气，从 20 分钟前那次 resume 起算
        assertEquals(20, r.sessionMinutes)
    }

    @Test
    fun `前台时长上限是 60 分钟`() {
        val events = listOf(ev(UsageEventType.RESUME, 120))
        val r = SessionMath.compute(events, now)
        assertEquals(60, r.foregroundMinutesLastHour)
        assertEquals(120, r.sessionMinutes)
    }

    @Test
    fun `未来时间戳被夹到现在`() {
        val events = listOf(UsageEvent(UsageEventType.RESUME, now + 10 * min))
        val r = SessionMath.compute(events, now)
        assertEquals(0, r.sessionMinutes)
    }
}