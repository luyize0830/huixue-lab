package com.example.blehr.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitiativeTest {

    private val hour = 15
    private val now = 1_000_000_000_000L
    private val threeHours = 3 * 60 * 60 * 1000L

    private fun state(
        lastTalkAt: Long? = now - threeHours,
        lastCheckAt: Long = 0L,
        lastOutreachAt: Long = 0L,
        countToday: Int = 0,
        hour: Int = this.hour,
        score: Int = 85,
        hasPending: Boolean = false
    ) = Initiative.State(
        now = now,
        lastTalkAt = lastTalkAt,
        lastCheckAt = lastCheckAt,
        lastOutreachAt = lastOutreachAt,
        countToday = countToday,
        hour = hour,
        score = score,
        hasPending = hasPending
    )

    @Test
    fun `深夜和清晨不主动找人`() {
        assertFalse(Initiative.shouldReachOut(state(hour = 23)))
        assertFalse(Initiative.shouldReachOut(state(hour = 3)))
        assertTrue(Initiative.inQuietHours(0))
        assertFalse(Initiative.inQuietHours(9))
    }

    @Test
    fun `已经有没读的话就不再叠一条`() {
        assertFalse(Initiative.shouldReachOut(state(hasPending = true)))
    }

    @Test
    fun `一天最多三次`() {
        assertTrue(Initiative.shouldReachOut(state(countToday = 2)))
        assertFalse(Initiative.shouldReachOut(state(countToday = 3)))
    }

    @Test
    fun `刚检查过就不重复检查`() {
        assertFalse(Initiative.shouldReachOut(state(lastCheckAt = now - 60_000L)))
    }

    @Test
    fun `两次主动之间至少隔一小时`() {
        assertFalse(Initiative.shouldReachOut(state(lastOutreachAt = now - 30 * 60 * 1000L)))
    }

    @Test
    fun `状态好的时候要等三小时`() {
        // 2.5 小时：不够
        assertFalse(Initiative.shouldReachOut(state(lastTalkAt = now - 150 * 60 * 1000L, score = 90)))
        // 3 小时整：够了
        assertTrue(Initiative.shouldReachOut(state(lastTalkAt = now - 180 * 60 * 1000L, score = 90)))
    }

    @Test
    fun `状态差的时候两小时就会想起你`() {
        assertTrue(Initiative.shouldReachOut(state(lastTalkAt = now - 130 * 60 * 1000L, score = 55)))
    }

    @Test
    fun `从没聊过天的不会主动上门`() {
        assertFalse(Initiative.shouldReachOut(state(lastTalkAt = null)))
    }

    @Test
    fun `担心的时候不绕弯子`() {
        val line = Initiative.line(hour = 15, mood = MascotMood.WORRIED, note = "在赶报告")
        assertTrue(line.contains("状态不太对"))
    }

    @Test
    fun `记得上次说的话就追问一句`() {
        val line = Initiative.line(hour = 15, mood = MascotMood.IDLE, note = "在图书馆", ownLife = 0)
        assertTrue(line.contains("上次你说「在图书馆」"))
        assertTrue(line.contains("后来呢"))
    }

    @Test
    fun `没有记忆时问一句在干嘛`() {
        val line = Initiative.line(hour = 9, mood = MascotMood.IDLE, note = null, ownLife = 1)
        assertTrue(line.startsWith("早。"))
        assertTrue(line.contains("在干嘛呢"))
        // 她自己也有事，不是一上来就查岗
        assertTrue(line.contains("刚睡醒"))
    }

    @Test
    fun `句子不会长得像一段汇报`() {
        val line = Initiative.line(hour = 20, mood = MascotMood.HAPPY, note = "组会", ownLife = 2)
        assertEquals(3, line.count { it == '。' || it == '？' })
        assertTrue(line.length <= 60)
    }
}