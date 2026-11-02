package com.example.blehr.logic

import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ExperimentRecord
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentReportTest {

    private val pool = listOf(
        ActionItem("walk", "动", "下楼走一圈", 5, ActionCategory.MOVE, "下楼"),
        ActionItem("breath", "静", "4-7-8 呼吸", 3, ActionCategory.CALM, "吸气"),
        ActionItem("video", "玩", "看一个搞笑视频", 3, ActionCategory.PLAY, "点开")
    )

    /** 造一个本地时间（小时可指定）的时间戳 */
    private fun at(hour: Int, day: Int = 1): Long = Calendar.getInstance().apply {
        set(Calendar.YEAR, 2026)
        set(Calendar.MONTH, Calendar.NOVEMBER)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun rec(
        actionId: String,
        rating: Int,
        hour: Int,
        day: Int = 1,
        hrBefore: Int? = 70,
        hrAfter: Int? = 60
    ) = ExperimentRecord(actionId, EnergyBand.MID, hrBefore, hrAfter, rating, at(hour, day))

    @Test
    fun `没有记录时给空报告而不是崩`() {
        val d = ExperimentReport.build(emptyList(), pool)
        assertEquals(0, d.total)
        assertEquals(0, d.days)
        assertNull(d.overallRating)
        assertNull(d.hrDownRate)
        assertFalse(d.enough)
        assertTrue(ExperimentReport.toMarkdown(emptyList(), pool).contains("还没有记录"))
    }

    @Test
    fun `同一类别的记录会聚在一起`() {
        val rs = listOf(
            rec("walk", 5, 10, hrAfter = 60),
            rec("walk", 4, 11, hrAfter = 65),
            rec("walk", 4, 12, hrAfter = 72)
        )
        val move = ExperimentReport.build(rs, pool).byCategory.first { it.label == ActionCategory.MOVE.label }
        assertEquals(3, move.n)
        assertEquals(4.333, move.avgRating!!, 0.01)
        assertEquals(100.0, move.effectiveRate!!, 0.01)
        // 心率变化 (-10, -5, +2) 平均 -4.33
        assertEquals(-4.333, move.avgHrDelta!!, 0.01)
    }

    @Test
    fun `按时段分桶：上午 下午 傍晚 深夜`() {
        val rs = listOf(rec("walk", 4, 9), rec("walk", 3, 14), rec("walk", 5, 20), rec("walk", 2, 2))
        val byHour = ExperimentReport.build(rs, pool).byHour
        assertEquals(listOf(1, 1, 1, 1), byHour.map { it.n })
        assertEquals("深夜 23-6 点", byHour[3].label)
        assertEquals(2.0, byHour[3].avgRating!!, 0.01)
    }

    @Test
    fun `覆盖天数按本地日期去重`() {
        val rs = listOf(rec("walk", 4, 9, day = 1), rec("walk", 4, 9, day = 1), rec("walk", 4, 9, day = 3))
        assertEquals(2, ExperimentReport.build(rs, pool).days)
    }

    @Test
    fun `组内不足三次不下排名`() {
        val rs = listOf(rec("walk", 5, 10), rec("walk", 5, 11), rec("breath", 1, 12))
        val d = ExperimentReport.build(rs, pool)
        assertNull(d.best)
        assertNull(d.worst)
        // 口径里必须写明"只列不排名"，否则用户会以为没排名是 bug
        assertTrue(d.markdownText().contains("只列不排名"))
    }

    @Test
    fun `够样本时给出最好与最差`() {
        val rs = listOf(
            rec("walk", 5, 10), rec("walk", 5, 11), rec("walk", 4, 12),
            rec("video", 1, 13), rec("video", 2, 14), rec("video", 1, 15)
        )
        val d = ExperimentReport.build(rs, pool)
        assertEquals(ActionCategory.MOVE.label, d.best)
        assertEquals(ActionCategory.PLAY.label, d.worst)
        assertEquals(4.667, d.bestAvg!!, 0.01)
        val md = d.markdownText()
        assertTrue(md.contains("## 按类别"))
        assertTrue(md.contains("| 动一动 | 3 | 4.7 |"))
        assertTrue(md.contains("| 玩一下 | 3 | 1.3 |"))
    }

    @Test
    fun `心率下降比例只统计前后都有值的记录`() {
        val rs = listOf(
            rec("walk", 5, 10, hrAfter = 60),
            rec("walk", 5, 11, hrAfter = 80),
            rec("walk", 5, 12, hrBefore = null, hrAfter = null)
        )
        assertEquals(50.0, ExperimentReport.build(rs, pool).hrDownRate!!, 0.01)
    }

    @Test
    fun `导出的 markdown 带口径与局限`() {
        val md = ExperimentReport.toMarkdown(listOf(rec("walk", 4, 9)), pool)
        assertTrue(md.contains("N=1"))
        assertTrue(md.contains("不做因果断言"))
        assertTrue(md.contains("| 类别 | n |"))
    }

    private fun com.example.blehr.logic.ReportData.markdownText(): String =
        ExperimentReport.toMarkdown(
            listOf(
                rec("walk", 5, 10), rec("walk", 5, 11), rec("walk", 4, 12),
                rec("video", 1, 13), rec("video", 2, 14), rec("video", 1, 15)
            ),
            pool
        )
}