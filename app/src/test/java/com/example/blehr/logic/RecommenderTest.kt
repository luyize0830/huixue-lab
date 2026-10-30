package com.example.blehr.logic

import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ExperimentRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recommender 单元测试。
 *
 * 随机性说明：Thompson 采样本身是随机的，所以"谁排第一"不做单次断言，
 * 而是用"好行动在多次抽样中胜出的比例"来验证算法方向是对的。
 */
class RecommenderTest {

    private val a1 = ActionItem("walk", "🚶", "下楼走一圈", 5, ActionCategory.MOVE, "别带手机")
    private val a2 = ActionItem("breath", "🌬", "四拍呼吸", 3, ActionCategory.CALM, "吸4停4呼4停4")
    private val a3 = ActionItem("water", "💧", "接杯水", 2, ActionCategory.FUEL, "喝完再回来")

    private val pool = listOf(a1, a2, a3)

    private fun rec(id: String, band: EnergyBand, rating: Int, before: Int? = null, after: Int? = null) =
        ExperimentRecord(id, band, before, after, rating, 0L)

    @Test
    fun `空池返回空列表`() {
        assertTrue(Recommender.recommend(EnergyBand.LOW, emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `返回数量不超过 limit 且不重复`() {
        val out = Recommender.recommend(EnergyBand.LOW, pool, emptyList(), limit = 2)
        assertEquals(2, out.size)
        assertEquals(2, out.map { it.action.id }.toSet().size)
    }

    @Test
    fun `没试过的行动会被标注出来`() {
        val out = Recommender.recommend(EnergyBand.LOW, pool, emptyList(), limit = 3)
        assertTrue(out.all { it.reason.contains("还没在这个状态下试过") })
        assertTrue(out.all { it.attemptsInBand == 0 })
    }

    @Test
    fun `只统计同档位的记录`() {
        val records = listOf(rec("walk", EnergyBand.LOW, 5))
        val out = Recommender.recommend(EnergyBand.MID, pool, records, limit = 3)
        val walk = out.first { it.action.id == "walk" }
        // LOW 档位的记录不应影响 MID 档位的统计
        assertEquals(0, walk.attemptsInBand)
    }

    @Test
    fun `持续高分行动在多次抽样中更容易排第一`() {
        val records = buildList {
            repeat(20) { add(rec("walk", EnergyBand.LOW, 5)) }
            repeat(20) { add(rec("breath", EnergyBand.LOW, 1)) }
        }
        var walkFirst = 0
        val rounds = 300
        // 只看"都试过"的两个行动：未被试过的行动有探索加成，不参与这个对比
        val tried = listOf(a1, a2)
        repeat(rounds) {
            val top = Recommender.recommend(EnergyBand.LOW, tried, records, limit = 1).first()
            if (top.action.id == "walk") walkFirst++
        }
        // Beta(21,1) 对 Beta(1,21)，胜出概率应远高于随机
        assertTrue("walk 排第一的比例过低：$walkFirst/$rounds", walkFirst > rounds * 0.9)
    }

    @Test
    fun `探索加成让冷启动的行动有机会被抽到`() {
        // 两个行动都试过很多次且都很差，新行动应当频繁出现
        val records = buildList {
            repeat(30) { add(rec("walk", EnergyBand.LOW, 1)) }
            repeat(30) { add(rec("breath", EnergyBand.LOW, 1)) }
        }
        var waterTop = 0
        repeat(200) {
            val top = Recommender.recommend(EnergyBand.LOW, pool, records, limit = 1).first()
            if (top.action.id == "water") waterTop++
        }
        assertTrue("冷启动行动被抽到的次数太少：$waterTop/200", waterTop > 40)
    }

    @Test
    fun `图谱统计计算平均分与心率变化`() {
        val records = listOf(
            rec("walk", EnergyBand.LOW, 5, before = 90, after = 78),
            rec("walk", EnergyBand.MID, 3, before = 88, after = 84),
            rec("breath", EnergyBand.LOW, 4, before = 85, after = 85)
        )
        val stats = Recommender.stats(pool, records)
        assertEquals(2, stats.size) // water 没有记录，不应出现

        val walk = stats.first { it.action.id == "walk" }
        assertEquals(2, walk.attempts)
        assertEquals(4.0, walk.avgRating, 0.001)
        assertNotNull(walk.avgHrDelta)
        assertEquals(-8.0, walk.avgHrDelta!!, 0.001) // (78-90 + 84-88) / 2

        val breath = stats.first { it.action.id == "breath" }
        assertEquals(0.0, breath.avgHrDelta!!, 0.001)
    }

    @Test
    fun `没有心率数据时心跳变化为 null`() {
        val stats = Recommender.stats(pool, listOf(rec("water", EnergyBand.LOW, 4)))
        assertEquals(1, stats.size)
        assertEquals(null, stats.first().avgHrDelta)
    }
}