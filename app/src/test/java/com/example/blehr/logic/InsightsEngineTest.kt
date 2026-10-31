package com.example.blehr.logic

import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ExperimentRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class InsightsEngineTest {

    private val pool = listOf(
        ActionItem("walk", "🚶", "下楼走一圈", 5, ActionCategory.MOVE, "cue"),
        ActionItem("lie", "🛏️", "平躺闭眼", 10, ActionCategory.REST, "cue")
    )

    private fun ts(hour: Int): Long =
        LocalDateTime.of(2026, 1, 1, hour, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `时段划分`() {
        assertEquals(0, InsightsEngine.bucketOf(8))
        assertEquals(1, InsightsEngine.bucketOf(14))
        assertEquals(2, InsightsEngine.bucketOf(20))
        assertEquals(3, InsightsEngine.bucketOf(2))
        assertEquals(3, InsightsEngine.bucketOf(23))
    }

    @Test
    fun `没有记录时给出引导而不是显示 0`() {
        val ins = InsightsEngine.compute(emptyList(), pool)
        assertEquals(0, ins.total)
        assertTrue(InsightsEngine.summaryLine(ins).contains("还没有记录"))
        assertEquals(4, ins.buckets.size)
    }

    @Test
    fun `有效率和心率下降率都算对`() {
        val recs = listOf(
            ExperimentRecord("walk", EnergyBand.LOW, 80, 70, 5, ts(10)),
            ExperimentRecord("walk", EnergyBand.LOW, 70, 75, 3, ts(11)),
            ExperimentRecord("lie", EnergyBand.LOW, null, null, 4, ts(20))
        )
        val ins = InsightsEngine.compute(recs, pool)
        assertEquals(3, ins.total)
        assertEquals(2.0 / 3.0, ins.effectiveRate, 0.001)
        // 只有两条同时有前后心率，其中一条下降
        assertEquals(0.5, ins.hrDownRate, 0.001)
    }

    @Test
    fun `时段统计只统计对应时段`() {
        val recs = listOf(
            ExperimentRecord("walk", EnergyBand.LOW, null, null, 5, ts(9)),
            ExperimentRecord("walk", EnergyBand.LOW, null, null, 3, ts(10)),
            ExperimentRecord("lie", EnergyBand.LOW, null, null, 4, ts(21))
        )
        val ins = InsightsEngine.compute(recs, pool)
        assertEquals(2, ins.buckets[0].count)   // 上午
        assertEquals(0, ins.buckets[1].count)   // 下午
        assertEquals(1, ins.buckets[2].count)   // 傍晚
        assertEquals(4.0, ins.buckets[0].avgRating!!, 0.001)
    }

    @Test
    fun `榜单按平均分排序`() {
        val recs = listOf(
            ExperimentRecord("lie", EnergyBand.LOW, null, null, 5, ts(9)),
            ExperimentRecord("lie", EnergyBand.LOW, null, null, 5, ts(10)),
            ExperimentRecord("walk", EnergyBand.LOW, null, null, 3, ts(11))
        )
        val ins = InsightsEngine.compute(recs, pool)
        assertEquals("lie", ins.top.first().action.id)
    }

    @Test
    fun `总结句里会点出最有效的时段`() {
        val recs = (0 until 3).map {
            ExperimentRecord("walk", EnergyBand.LOW, null, null, 5, ts(9))
        }
        val line = InsightsEngine.summaryLine(InsightsEngine.compute(recs, pool))
        assertTrue(line.contains("3 次实验"))
        assertTrue(line.contains("上午"))
    }

    @Test
    fun `导出的 CSV 带表头并转义逗号`() {
        val recs = listOf(
            ExperimentRecord("walk", EnergyBand.LOW, 80, 72, 5, ts(9)),
            ExperimentRecord("unknown", EnergyBand.MID, null, null, 3, ts(10))
        )
        val csv = RecordCsv.toCsv(recs, pool)
        val lines = csv.trim().split("\n")
        assertTrue(lines[0].startsWith("时间,行动,当时状态"))
        assertEquals(3, lines.size)
        assertTrue(lines[1].contains("下楼走一圈"))
        assertTrue(lines[2].contains("unknown"))  // 池里没有的行动，退化成 id 而不是崩
    }

    @Test
    fun `行动标题里有逗号时会被引号包起来`() {
        val weird = listOf(ActionItem("x", "x", "走一走, 再回来", 5, ActionCategory.MOVE, "cue"))
        val recs = listOf(ExperimentRecord("x", EnergyBand.LOW, null, null, 4, ts(9)))
        val csv = RecordCsv.toCsv(recs, weird)
        assertTrue(csv.contains("\"走一走, 再回来\""))
    }
}