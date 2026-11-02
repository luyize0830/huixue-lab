package com.example.blehr.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 这两组测试守着同一个风险：HRS 的字节布局按顺序排，读错一个字段
 * 不会抛异常，只会安静地算出错误的心率或把能量消耗当成 R-R。
 */
class HrFrameParseTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `一字节心率的普通帧`() {
        val f = HeartRateClient.parseFrame(bytes(0x00, 62))!!
        assertEquals(62, f.bpm)
        assertTrue(f.rrMs.isEmpty())
    }

    @Test
    fun `两字节心率按小端解`() {
        val f = HeartRateClient.parseFrame(bytes(0x01, 0x2C, 0x01))!!
        assertEquals(300, f.bpm)
        assertTrue(f.rrMs.isEmpty())
    }

    @Test
    fun `带 R-R 间期的帧按 1 1024 秒换算成毫秒`() {
        // flags=0x10 → 后面每 2 字节是一个 R-R；0x0004 = 4 → 4*1000/1024 ≈ 4 ms（极端值也照规则算）
        // 用 1024（=1000 ms）和 973（≈950 ms）两个值更接近真实
        val f = HeartRateClient.parseFrame(
            bytes(0x10, 60, 0x00, 0x04, 0xCD, 0x03)
        )!!
        assertEquals(60, f.bpm)
        assertEquals(listOf(1000, 950), f.rrMs)
    }

    @Test
    fun `有能量消耗字段时不会把它当成 R-R`() {
        // flags=0x18：bit3 能量消耗 2 字节 + bit4 R-R
        val f = HeartRateClient.parseFrame(
            bytes(0x18, 60, 0xFF, 0xFF, 0x00, 0x04)
        )!!
        assertEquals(60, f.bpm)
        assertEquals(listOf(1000), f.rrMs)
    }

    @Test
    fun `两字节心率加 R-R 一起出现`() {
        val f = HeartRateClient.parseFrame(
            bytes(0x11, 0x2C, 0x01, 0x00, 0x04)
        )!!
        assertEquals(300, f.bpm)
        assertEquals(listOf(1000), f.rrMs)
    }

    @Test
    fun `空值和截断帧返回 null 而不是崩溃`() {
        assertNull(HeartRateClient.parseFrame(null))
        assertNull(HeartRateClient.parseFrame(bytes()))
        assertNull(HeartRateClient.parseFrame(bytes(0x01)))
        assertNull(HeartRateClient.parseFrame(bytes(0x01, 0x2C)))
    }

    @Test
    fun `旧入口 parseHeartRate 仍然只返回心率`() {
        assertEquals(62, HeartRateClient.parseHeartRate(bytes(0x00, 62)))
        assertEquals(300, HeartRateClient.parseHeartRate(bytes(0x01, 0x2C, 0x01)))
    }
}