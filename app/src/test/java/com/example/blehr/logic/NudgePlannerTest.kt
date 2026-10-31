package com.example.blehr.logic

import com.example.blehr.model.EnergyBand
import com.example.blehr.model.StateAssessment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NudgePlannerTest {

    private fun assess(score: Int, band: EnergyBand = EnergyBand.LOW) =
        StateAssessment(band, score, "headline", emptyList())

    private val action = "walk" to "下楼走一圈"

    @Test
    fun `状态好又没久坐时不打扰`() {
        val n = NudgePlanner.plan(assess(80, EnergyBand.HIGH), 20, action, 0L, 10_000L)
        assertNull(n)
    }

    @Test
    fun `久坐超阈值会提醒`() {
        val n = NudgePlanner.plan(assess(80, EnergyBand.HIGH), 75, action, 0L, 10_000L)
        assertNotNull(n)
        assertEquals("坐得有点久了", n!!.title)
        assertEquals("walk", n.actionId)
    }

    @Test
    fun `久坐且状态差时说明两件事`() {
        val n = NudgePlanner.plan(assess(35), 90, action, 0L, 10_000L)
        assertNotNull(n)
        assertEquals("已经 90 分钟没动了", n!!.title)
        assertEquals("walk", n.actionId)
    }

    @Test
    fun `分数偏低但久坐未知时只提状态`() {
        val n = NudgePlanner.plan(assess(40), null, action, 0L, 10_000L)
        assertNotNull(n)
        assertEquals(true, n!!.body.contains("下楼走一圈"))
    }

    @Test
    fun `两次提醒之间至少隔 50 分钟`() {
        val last = 1_000_000L
        val tooSoon = NudgePlanner.plan(assess(30), 90, action, last, last + 10 * 60 * 1000L)
        assertNull(tooSoon)
        val later = NudgePlanner.plan(assess(30), 90, action, last, last + 51 * 60 * 1000L)
        assertNotNull(later)
    }

    @Test
    fun `用户关掉提醒后一律不产生`() {
        val n = NudgePlanner.plan(assess(20), 120, action, 0L, 10_000L, enabled = false)
        assertNull(n)
    }

    @Test
    fun `没有推荐行动时也能给出兜底文案`() {
        val n = NudgePlanner.plan(assess(30), 90, null, 0L, 10_000L)
        assertNotNull(n)
        assertEquals(true, n!!.body.contains("起来走两步"))
    }
}