package com.example.blehr.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HrvMathTest {

    /** 稳定节律：60 bpm，每拍 1000 ms */
    private fun steady(n: Int = 20) = List(n) { 1000 }

    @Test
    fun `间隔完全一致时 RMSSD 为 0`() {
        assertEquals(0.0, HrvMath.rmssd(steady())!!, 0.001)
        assertEquals(0.0, HrvMath.sdnn(steady())!!, 0.001)
        assertEquals(0.0, HrvMath.pnn50(steady())!!, 0.001)
    }

    @Test
    fun `样本不足时不出指标而不是瞎算`() {
        val few = listOf(1000, 1010, 990, 1005, 995, 1002, 998)
        assertNull(HrvMath.rmssd(few))
        assertNull(HrvMath.sdnn(few))
        assertNull(HrvMath.pnn50(few))
    }

    @Test
    fun `伪迹会被丢掉：300 到 2200 毫秒之外一律不算`() {
        val dirty = listOf(50, 1000, 1000, 5000, 1000, 1000, 1000, 1000, 1000, 1000, 1000)
        assertEquals(9, HrvMath.sanitize(dirty).size)
        assertEquals(0.0, HrvMath.rmssd(dirty)!!, 0.001)
    }

    @Test
    fun `RMSSD 按相邻差的均方根算`() {
        // 差值为 100、-100 交替：均方根 = 100
        val rr = List(10) { if (it % 2 == 0) 900 else 1000 }
        assertEquals(100.0, HrvMath.rmssd(rr)!!, 0.01)
    }

    @Test
    fun `pNN50 算的是超过 50 毫秒的相邻差占比`() {
        // 9 个差值里 4 个超过 50ms → 4/9 ≈ 44.44%
        val rr = listOf(1000, 1060, 1000, 1060, 1000, 1000, 1000, 1000, 1000, 1000)
        assertEquals(4 * 100.0 / 9.0, HrvMath.pnn50(rr)!!, 0.01)
    }

    @Test
    fun `SDNN 是总体标准差`() {
        val rr = listOf(980, 1000, 1020, 1000, 980, 1000, 1020, 1000)
        // 均值 1000，偏差平方均值 = (400*4 + 0*4)/8 = 200 → 标准差 ≈ 14.142
        assertEquals(14.142, HrvMath.sdnn(rr)!!, 0.01)
    }

    @Test
    fun `空列表不会崩`() {
        assertNull(HrvMath.rmssd(emptyList()))
        assertNull(HrvMath.sdnn(emptyList()))
        assertNull(HrvMath.pnn50(emptyList()))
        assertNull(HrvMath.plainLabel(null))
    }

    @Test
    fun `人话标签分三档`() {
        val low = HrvMath.plainLabel(15.0)
        val mid = HrvMath.plainLabel(35.0)
        val high = HrvMath.plainLabel(70.0)
        assertNotNull(low)
        assertEquals("波动偏小，可能还绷着", low)
        assertEquals("波动正常", mid)
        assertEquals("波动较大，比较松", high)
    }
}