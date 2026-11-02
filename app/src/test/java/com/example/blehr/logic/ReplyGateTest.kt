package com.example.blehr.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyGateTest {

    @Test
    fun `有句号就是说完`() {
        assertFalse(ReplyGate.looksUnfinished("今天好累。"))
        assertFalse(ReplyGate.looksUnfinished("睡了吗？"))
        assertFalse(ReplyGate.looksUnfinished("我服了！"))
    }

    @Test
    fun `完整短句不算半句`() {
        assertFalse(ReplyGate.looksUnfinished("好的"))
        assertFalse(ReplyGate.looksUnfinished("算了"))
        assertFalse(ReplyGate.looksUnfinished("嗯嗯"))
        assertFalse(ReplyGate.looksUnfinished("哈哈"))
    }

    @Test
    fun `以虚词结尾像是没说完`() {
        assertTrue(ReplyGate.looksUnfinished("我今天是真的"))
        assertTrue(ReplyGate.looksUnfinished("那个东西我放哪了"))
        assertTrue(ReplyGate.looksUnfinished("要不我们明天再"))
    }

    @Test
    fun `以逗号结尾肯定是没说完`() {
        assertTrue(ReplyGate.looksUnfinished("我刚想起来，"))
        assertTrue(ReplyGate.looksUnfinished("其实吧、"))
    }

    @Test
    fun `连发多条时合并窗口不变`() {
        assertEquals(ReplyGate.BATCH_MS, ReplyGate.waitMs(listOf("今天好累。", "啥也不想干。")))
    }

    @Test
    fun `最后一条没说完会多等四秒`() {
        val normal = ReplyGate.waitMs(listOf("今天好累。"))
        val half = ReplyGate.waitMs(listOf("今天好累。", "而且我"))
        assertEquals(ReplyGate.BATCH_MS + ReplyGate.UNFINISHED_EXTRA_MS, half)
        assertTrue(half > normal)
    }

    @Test
    fun `等待永远不超过上限`() {
        assertTrue(ReplyGate.waitMs(listOf("我")) <= ReplyGate.MAX_WAIT_MS)
        assertEquals(0L, ReplyGate.waitMs(emptyList()))
        assertEquals(0L, ReplyGate.waitMs(listOf("   ")))
    }

    @Test
    fun `合并时单条原样多行加换行`() {
        assertEquals("好累", ReplyGate.merge(listOf("  好累  ")))
        assertEquals("好累\n不想动", ReplyGate.merge(listOf("好累", "不想动")))
        assertEquals("", ReplyGate.merge(listOf("", "  ")))
    }

    @Test
    fun `时间感按分钟算`() {
        val now = 1_700_000_000_000L
        assertNull(ReplyGate.minutesSince(null, now))
        assertEquals(0, ReplyGate.minutesSince(now - 30_000L, now))
        assertEquals(12, ReplyGate.minutesSince(now - 12 * 60_000L, now))
        assertEquals(0, ReplyGate.minutesSince(now + 60_000L, now))
    }
}