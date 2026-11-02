package com.example.blehr.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmTest {

    @Test
    fun `深夜回得比白天慢`() {
        val night = Rhythm.plan(parts = 2, userChars = 10, hour = 1, mood = MascotMood.IDLE, roll = 0.5)
        val day = Rhythm.plan(parts = 2, userChars = 10, hour = 15, mood = MascotMood.IDLE, roll = 0.5)
        assertTrue(night.firstDelayMs > day.firstDelayMs)
        assertTrue(night.sleepy)
    }

    @Test
    fun `对方只说几个字时不会憋很久`() {
        val short = Rhythm.plan(parts = 1, userChars = 2, hour = 15, mood = MascotMood.IDLE, roll = 0.5)
        val long = Rhythm.plan(parts = 1, userChars = 60, hour = 15, mood = MascotMood.IDLE, roll = 0.5)
        assertTrue(short.firstDelayMs < long.firstDelayMs)
    }

    @Test
    fun `担心的时候回得更快`() {
        val worried = Rhythm.plan(parts = 2, userChars = 10, hour = 15, mood = MascotMood.WORRIED, roll = 0.5)
        val idle = Rhythm.plan(parts = 2, userChars = 10, hour = 15, mood = MascotMood.IDLE, roll = 0.5)
        assertTrue(worried.firstDelayMs < idle.firstDelayMs)
    }

    @Test
    fun `条数不会超过三条`() {
        val plan = Rhythm.plan(parts = 9, userChars = 10, hour = 15, mood = MascotMood.HAPPY, roll = 0.5)
        assertEquals(Humanizer.MAX_BUBBLES, plan.bubbles)
    }

    @Test
    fun `没有内容时不排任何延迟`() {
        val plan = Rhythm.plan(parts = 0, userChars = 10, hour = 15, mood = MascotMood.IDLE, roll = 0.5)
        assertEquals(0, plan.bubbles)
        assertEquals(0L, plan.firstDelayMs)
    }

    @Test
    fun `延迟有上下界，不会离谱`() {
        val fast = Rhythm.plan(parts = 1, userChars = 0, hour = 12, mood = MascotMood.WORRIED, roll = 0.0)
        val slow = Rhythm.plan(parts = 1, userChars = 999, hour = 3, mood = MascotMood.IDLE, roll = 1.0)
        assertTrue(fast.firstDelayMs >= 500L)
        assertTrue(slow.firstDelayMs <= 5200L)
        assertTrue(slow.gapMs >= fast.gapMs)
    }
}