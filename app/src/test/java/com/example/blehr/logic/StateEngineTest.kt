package com.example.blehr.logic

import com.example.blehr.model.EnergyBand
import com.example.blehr.model.SensorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StateEngine 单元测试。
 *
 * 这些用例同时是"可解释性"的规格说明：每一条扣分都必须能在 reasons 里找到对应文字。
 */
class StateEngineTest {

    @Test
    fun `没有任何信号时给满分并如实说明`() {
        val r = StateEngine.assess(SensorSnapshot())
        assertEquals(100, r.score)
        assertEquals(EnergyBand.HIGH, r.band)
        assertTrue(r.reasons.any { it.contains("没连上手环") })
    }

    @Test
    fun `心率高于基线15以上扣22分`() {
        val r = StateEngine.assess(
            SensorSnapshot(heartRate = 95, restingHr = 70, hourOfDay = 12)
        )
        assertEquals(78, r.score)
        assertTrue(r.reasons.any { it.contains("70 bpm") && it.contains("高 25 bpm") })
    }

    @Test
    fun `心率略高于基线扣10分`() {
        val r = StateEngine.assess(
            SensorSnapshot(heartRate = 82, restingHr = 70)
        )
        assertEquals(90, r.score)
        assertTrue(r.reasons.any { it.contains("略高于基线") })
    }

    @Test
    fun `心率接近基线与有心率无基线两种降级路径`() {
        val near = StateEngine.assess(SensorSnapshot(heartRate = 72, restingHr = 70))
        assertEquals(100, near.score)
        assertTrue(near.reasons.any { it.contains("接近基线") })

        val noBase = StateEngine.assess(SensorSnapshot(heartRate = 88, restingHr = null))
        assertEquals(100, noBase.score)
        assertTrue(noBase.reasons.any { it.contains("还没建立基线") })
    }

    @Test
    fun `连续专注时长分三档扣分`() {
        assertEquals(94, StateEngine.assess(SensorSnapshot(focusMinutes = 60)).score)
        assertEquals(88, StateEngine.assess(SensorSnapshot(focusMinutes = 90)).score)
        assertEquals(82, StateEngine.assess(SensorSnapshot(focusMinutes = 120)).score)
        // 不满 60 分钟不扣分，但仍会写一条依据
        val short = StateEngine.assess(SensorSnapshot(focusMinutes = 30))
        assertEquals(100, short.score)
        assertTrue(short.reasons.any { it.contains("刚专注 30 分钟") })
    }

    @Test
    fun `深夜时段扣15分`() {
        assertEquals(85, StateEngine.assess(SensorSnapshot(hourOfDay = 23)).score)
        assertEquals(85, StateEngine.assess(SensorSnapshot(hourOfDay = 3)).score)
        assertEquals(100, StateEngine.assess(SensorSnapshot(hourOfDay = 5)).score)
    }

    @Test
    fun `屏幕时间超过40分钟扣8分`() {
        assertEquals(92, StateEngine.assess(SensorSnapshot(screenMinutesLastHour = 40)).score)
        assertEquals(100, StateEngine.assess(SensorSnapshot(screenMinutesLastHour = 39)).score)
    }

    @Test
    fun `所有信号都差时落到低电量档`() {
        val r = StateEngine.assess(
            SensorSnapshot(
                heartRate = 100,
                restingHr = 65,
                focusMinutes = 150,
                screenMinutesLastHour = 55,
                hourOfDay = 2
            )
        )
        // 22 + 18 + 15 + 8 = 63
        assertEquals(37, r.score)
        assertEquals(EnergyBand.LOW, r.band)
        assertTrue(r.headline.contains("回 10%"))
        assertEquals(4, r.reasons.size)
    }

    @Test
    fun `档位阈值为40与70`() {
        // 70 分及以上为 HIGH：22 + 8 = 30 扣分
        val high = StateEngine.assess(
            SensorSnapshot(heartRate = 95, restingHr = 70, screenMinutesLastHour = 45)
        )
        assertEquals(70, high.score)
        assertEquals(EnergyBand.HIGH, high.band)

        // 45 分落在 MID：22 + 18 + 15 = 55 扣分
        val mid = StateEngine.assess(
            SensorSnapshot(heartRate = 95, restingHr = 70, focusMinutes = 120, hourOfDay = 23)
        )
        assertEquals(45, mid.score)
        assertEquals(EnergyBand.MID, mid.band)
    }
}