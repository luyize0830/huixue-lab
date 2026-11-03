package com.example.blehr.player

import com.example.blehr.logic.AmbientTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 合成器是纯函数，所以能在 JVM 里测。
 * 这里不测"好不好听"（测不出来），只钉住四条底线。
 */
class SynthTest {

    @Test
    fun `采样值不越界也没有 NaN`() {
        AmbientTrack.entries.forEach { track ->
            var t = 0.0
            repeat(400) {
                val v = Synth.sample(track, t)
                assertTrue("$track 在 $t 秒越界：$v", v in -1f..1f)
                assertTrue("$track 在 $t 秒是 NaN", !v.isNaN())
                t += 0.37
            }
        }
    }

    @Test
    fun `同一时刻的采样是稳定的`() {
        AmbientTrack.entries.forEach { track ->
            assertEquals(Synth.sample(track, 3.21), Synth.sample(track, 3.21), 0f)
        }
    }

    @Test
    fun `三段音景听起来不是同一段`() {
        val rain = Synth.sample(AmbientTrack.RAIN, 3.21)
        val night = Synth.sample(AmbientTrack.NIGHT, 3.21)
        val breath = Synth.sample(AmbientTrack.BREATH, 3.21)
        assertNotEquals(rain, night)
        assertNotEquals(rain, breath)
        assertNotEquals(night, breath)
    }

    @Test
    fun `不是静音也不是一直同一个值`() {
        AmbientTrack.entries.forEach { track ->
            var maxAbs = 0.0
            var sum = 0.0
            val n = 22050
            for (i in 0 until n step 10) {
                val v = Synth.sample(track, i.toDouble() / Synth.SAMPLE_RATE).toDouble()
                maxAbs = maxOf(maxAbs, kotlin.math.abs(v))
                sum += v * v
            }
            val rms = kotlin.math.sqrt(sum / (n / 10))
            assertTrue("$track 几乎没有声音（maxAbs=$maxAbs）", maxAbs > 0.01)
            assertTrue("$track 全程一个电平，像测试音", rms > 0.002)
        }
    }
}