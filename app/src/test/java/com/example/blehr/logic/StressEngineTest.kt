package com.example.blehr.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 压力/情绪推断的边界。
 *
 * 这些断言的意思是：规则不能随口改——改一个阈值，测试就会告诉你哪些说法跟着变了。
 */
class StressEngineTest {

    @Test
    fun `没有生理数据时宁可说测不到`() {
        val r = StressEngine.assess(StressInput(hour = 15))
        assertEquals(StressBand.UNKNOWN, r.band)
        assertFalse(r.measuredPhysiology)
        assertTrue(r.reasons.any { it.contains("只是按作息和行为估的") })
        assertEquals(0, r.score)
    }

    @Test
    fun `心率变异率很低且心率上来了算偏紧`() {
        val r = StressEngine.assess(
            StressInput(heartRate = 95, restingHr = 70, hrvRmssd = 15.0, hour = 15)
        )
        // 40（RMSSD<20）+ 24（心率高 25）
        assertEquals(64, r.score)
        assertEquals(StressBand.HIGH, r.band)
        assertEquals(Emotion.TENSE, r.emotion)
        assertTrue(r.measuredPhysiology)
    }

    @Test
    fun `一切正常算放松`() {
        val r = StressEngine.assess(
            StressInput(heartRate = 68, restingHr = 66, hrvRmssd = 60.0, hour = 15)
        )
        assertEquals(0, r.score)
        assertEquals(StressBand.CALM, r.band)
        assertEquals(Emotion.CALM, r.emotion)
        assertTrue(r.measuredPhysiology)
    }

    @Test
    fun `没有手环时专注与深夜仍会累加但不下压力结论`() {
        val r = StressEngine.assess(StressInput(focusMinutes = 130, hour = 1))
        // 10（专注≥120）+ 8（深夜）
        assertEquals(18, r.score)
        assertEquals(StressBand.UNKNOWN, r.band)
        assertFalse(r.measuredPhysiology)
    }

    @Test
    fun `深夜且精力低推断为累`() {
        val r = StressEngine.assess(
            StressInput(heartRate = 70, restingHr = 70, hrvRmssd = 60.0, hour = 1, energyScore = 30)
        )
        assertEquals(Emotion.TIRED, r.emotion)
    }

    @Test
    fun `高压且屏幕久推断为烦躁`() {
        val r = StressEngine.assess(
            StressInput(
                heartRate = 75, restingHr = 70, hrvRmssd = 10.0,
                focusMinutes = 120, screenMinutesLastHour = 60, hour = 1
            )
        )
        // 40 + 10 + 8 + 6 = 64
        assertEquals(64, r.score)
        assertEquals(StressBand.HIGH, r.band)
        assertEquals(Emotion.IRRITABLE, r.emotion)
    }

    @Test
    fun `分数永远在 0 到 100 之间`() {
        val r = StressEngine.assess(
            StressInput(
                heartRate = 200, restingHr = 50, hrvRmssd = 5.0,
                focusMinutes = 500, screenMinutesLastHour = 90, hour = 3, energyScore = 5
            )
        )
        assertTrue(r.score in 0..100)
        assertEquals(StressBand.HIGH, r.band)
        // 88 分，离 100 还有余量：分数是用来沟通的，不做到满格吓人
        assertEquals(88, r.score)
    }

    @Test
    fun `每条依据都是一句人话`() {
        val r = StressEngine.assess(
            StressInput(heartRate = 95, restingHr = 70, hrvRmssd = 15.0, focusMinutes = 100, hour = 0)
        )
        assertTrue(r.reasons.size >= 4)
        assertTrue(r.reasons.all { it.length >= 6 })
    }

    @Test
    fun `只有心率没有基线时不算压力`() {
        val r = StressEngine.assess(StressInput(heartRate = 88, hour = 15))
        assertFalse(r.measuredPhysiology)
        assertEquals(StressBand.UNKNOWN, r.band)
        assertFalse(r.reasons.any { it.contains("比平时高") })
    }

    @Test
    fun `正常心率波动不扣分`() {
        val r = StressEngine.assess(
            StressInput(heartRate = 70, restingHr = 70, hrvRmssd = 45.0, hour = 15)
        )
        assertEquals(0, r.score)
        assertTrue(r.reasons.any { it.contains("心率波动正常") })
    }
}