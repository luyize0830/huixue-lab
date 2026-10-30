package com.example.blehr.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 久坐口径测试。
 *
 * 关键约定：拿不到"上次走动时间"时返回 null（表示不知道），绝不用 0 冒充"刚动过"，
 * 也不要冒充"很久没动"——这个区别会直接体现在给用户的建议上。
 */
class ActivityTrackerTest {

    private val now = 1_700_000_000_000L
    private val min = 60_000L

    @Test
    fun `从没记录过走动时返回 null`() {
        assertNull(ActivityTracker.stillMinutes(0L, now))
    }

    @Test
    fun `四十五分钟前走过 就是四十五分钟`() {
        assertEquals(45, ActivityTracker.stillMinutes(now - 45 * min, now))
    }

    @Test
    fun `超过八小时按八小时算 避免出现离谱数字`() {
        assertEquals(8 * 60, ActivityTracker.stillMinutes(now - 30 * 60 * min, now))
    }

    @Test
    fun `时间倒流时返回 null 而不是负数`() {
        assertNull(ActivityTracker.stillMinutes(now + 5 * min, now))
    }

    @Test
    fun `不足一分钟记为 0 分钟`() {
        assertEquals(0, ActivityTracker.stillMinutes(now - 30_000L, now))
    }
}