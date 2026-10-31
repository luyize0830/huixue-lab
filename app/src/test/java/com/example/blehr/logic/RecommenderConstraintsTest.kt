package com.example.blehr.logic

import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ExperimentRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderConstraintsTest {

    private fun item(id: String, minutes: Int, cat: ActionCategory) =
        ActionItem(id, "x", id, minutes, cat, "cue")

    private val pool = listOf(
        item("walk", 5, ActionCategory.MOVE),
        item("lie", 10, ActionCategory.REST),
        item("breath", 3, ActionCategory.CALM),
        item("water", 1, ActionCategory.FUEL),
        item("call", 5, ActionCategory.SOCIAL)
    )

    @Test
    fun `时间预算会过滤掉太久的行动`() {
        val recs = Recommender.recommend(
            EnergyBand.MID, pool, emptyList(), 10,
            RecommendationConstraints(maxMinutes = 3)
        )
        assertTrue(recs.isNotEmpty())
        assertTrue(recs.all { it.action.minutes <= 3 })
    }

    @Test
    fun `排除列表生效`() {
        val recs = Recommender.recommend(
            EnergyBand.MID, pool, emptyList(), 10,
            RecommendationConstraints(excludeIds = setOf("walk", "lie", "breath", "water"))
        )
        assertEquals(listOf("call"), recs.map { it.action.id })
    }

    @Test
    fun `深夜模式优先给能收尾的行动`() {
        val recs = Recommender.recommend(
            EnergyBand.LOW, pool, emptyList(), 3,
            RecommendationConstraints(nightMode = true)
        )
        assertTrue(recs.isNotEmpty())
        assertTrue(recs.all { it.action.category in RecommendationConstraints.NIGHT_FRIENDLY })
    }

    @Test
    fun `深夜候选不够时会退回全量而不是推空`() {
        val tiny = listOf(item("walk", 5, ActionCategory.MOVE), item("breath", 3, ActionCategory.CALM))
        val recs = Recommender.recommend(
            EnergyBand.LOW, tiny, emptyList(), 3,
            RecommendationConstraints(nightMode = true)
        )
        assertEquals(2, recs.size)
    }

    @Test
    fun `约束叠加后为空时返回空列表而不是崩`() {
        val recs = Recommender.recommend(
            EnergyBand.MID, pool, emptyList(), 3,
            RecommendationConstraints(maxMinutes = 0)
        )
        assertTrue(recs.isEmpty())
    }

    @Test
    fun `学过有效记录的行动更容易被选中`() {
        // 300 次抽样里，有"8 次全 5 分"的行动应该显著压过"8 次全 1 分"的
        val twoItemPool = listOf(item("walk", 5, ActionCategory.MOVE), item("lie", 10, ActionCategory.REST))
        val records = buildList {
            repeat(8) {
                add(ExperimentRecord("walk", EnergyBand.LOW, 80, 72, 5, 1000L + it))
                add(ExperimentRecord("lie", EnergyBand.LOW, 80, 80, 1, 2000L + it))
            }
        }
        var walkWins = 0
        repeat(300) {
            val recs = Recommender.recommend(EnergyBand.LOW, twoItemPool, records, 1)
            if (recs.first().action.id == "walk") walkWins++
        }
        assertTrue("walk 应该赢超过 80%，实际 $walkWins", walkWins > 240)
    }
}