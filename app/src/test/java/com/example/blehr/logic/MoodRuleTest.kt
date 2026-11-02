package com.example.blehr.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodRuleTest {

    @Test
    fun `低于 70 是担心`() {
        assertEquals(MascotMood.WORRIED, MoodRule.of(0))
        assertEquals(MascotMood.WORRIED, MoodRule.of(69))
        assertEquals(MascotMood.WORRIED, MoodRule.of(37))
    }

    @Test
    fun `70 到 84 是发呆`() {
        assertEquals(MascotMood.IDLE, MoodRule.of(70))
        assertEquals(MascotMood.IDLE, MoodRule.of(73))
        assertEquals(MascotMood.IDLE, MoodRule.of(84))
    }

    @Test
    fun `85 以上是开心`() {
        assertEquals(MascotMood.HAPPY, MoodRule.of(85))
        assertEquals(MascotMood.HAPPY, MoodRule.of(100))
    }

    @Test
    fun `三态覆盖且互斥`() {
        val kinds = (0..100).map { MoodRule.of(it) }.toSet()
        assertEquals(3, kinds.size)
        assertEquals("在担心你", MoodRule.label(MascotMood.WORRIED))
        assertFalse(MoodRule.label(MascotMood.IDLE).isEmpty())
    }
}