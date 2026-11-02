package com.example.blehr.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 鱼的情绪规则单测：阈值边界必须钉死，否则以后调权重会悄悄改掉产品承诺。
 */
class FishMoodRuleTest {

    @Test
    fun `低于七十分就担心`() {
        assertEquals(FishMood.WORRIED, FishMoodRule.of(0))
        assertEquals(FishMood.WORRIED, FishMoodRule.of(59))
        assertEquals(FishMood.WORRIED, FishMoodRule.of(69))
    }

    @Test
    fun `七十到八十四是发呆`() {
        assertEquals(FishMood.IDLE, FishMoodRule.of(70))
        assertEquals(FishMood.IDLE, FishMoodRule.of(73))
        assertEquals(FishMood.IDLE, FishMoodRule.of(84))
    }

    @Test
    fun `八十五以上才开心`() {
        assertEquals(FishMood.HAPPY, FishMoodRule.of(85))
        assertEquals(FishMood.HAPPY, FishMoodRule.of(100))
    }

    @Test
    fun `三档都覆盖且互斥`() {
        val seen = (0..100).map { FishMoodRule.of(it) }.toSet()
        assertEquals(3, seen.size)
    }
}