package com.example.blehr.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "什么时候该推音乐"是一组很保守的规则：宁可漏推，也不要变成每天弹广告。
 */
class MusicSuggestionTest {

    private fun stress(
        band: StressBand,
        emotion: Emotion = Emotion.CALM,
        score: Int = if (band == StressBand.HIGH) 70 else 20
    ) = StressResult(
        score = score,
        band = band,
        emotion = emotion,
        reasons = emptyList(),
        measuredPhysiology = band != StressBand.UNKNOWN
    )

    @Test
    fun `关掉开关就彻底不推`() {
        val s = stress(StressBand.HIGH)
        assertFalse(
            MusicSuggestion.shouldSuggest(s, 15, 10, 0L, now = 1_000L, enabled = false)
        )
    }

    @Test
    fun `测不到压力不推`() {
        val s = stress(StressBand.UNKNOWN)
        assertFalse(MusicSuggestion.shouldSuggest(s, 15, 10, 0L, now = 1_000L))
    }

    @Test
    fun `白天只有偏紧才推`() {
        val mild = stress(StressBand.MILD, Emotion.TIRED)
        val high = stress(StressBand.HIGH, Emotion.TENSE)
        assertFalse(MusicSuggestion.shouldSuggest(mild, 15, 10, 0L, now = 1_000L))
        assertTrue(MusicSuggestion.shouldSuggest(high, 15, 10, 0L, now = 1_000L))
    }

    @Test
    fun `90 分钟内不重复推`() {
        val s = stress(StressBand.HIGH)
        val now = 10_000_000L
        assertFalse(
            MusicSuggestion.shouldSuggest(s, 15, 10, now - 60_000L, now)
        )
        assertTrue(
            MusicSuggestion.shouldSuggest(s, 15, 10, now - MusicSuggestion.COOLDOWN_MS - 1, now)
        )
    }

    @Test
    fun `半夜只在用户还醒着的时候开口`() {
        val mild = stress(StressBand.MILD, Emotion.TIRED)
        assertFalse(MusicSuggestion.shouldSuggest(mild, 2, 5, 0L, now = 1_000L))
        assertTrue(MusicSuggestion.shouldSuggest(mild, 2, 30, 0L, now = 1_000L))
        // 半夜但状态是放松的，不推
        assertFalse(
            MusicSuggestion.shouldSuggest(stress(StressBand.CALM), 2, 30, 0L, now = 1_000L)
        )
    }

    @Test
    fun `绷着的时候放呼吸`() {
        assertEquals(AmbientTrack.BREATH, MusicSuggestion.pickFor(Emotion.TENSE))
        assertEquals(AmbientTrack.BREATH, MusicSuggestion.pickFor(Emotion.IRRITABLE))
    }

    @Test
    fun `累的时候放夜里的风`() {
        assertEquals(AmbientTrack.NIGHT, MusicSuggestion.pickFor(Emotion.TIRED))
        assertEquals(AmbientTrack.NIGHT, MusicSuggestion.pickFor(Emotion.LOW))
    }

    @Test
    fun `半夜绷着还是先放呼吸而不是风`() {
        // 夜里默认给「夜里的风」收尾，但绷着的时候呼吸更管用 —— 这条是刻意的例外
        val tense = MusicSuggestion.plan(stress(StressBand.HIGH, Emotion.TENSE), hour = 2)
        assertEquals(AmbientTrack.BREATH, tense.track)
        val tired = MusicSuggestion.plan(stress(StressBand.MILD, Emotion.TIRED), hour = 2)
        assertEquals(AmbientTrack.NIGHT, tired.track)
        val calmPlan = MusicSuggestion.plan(stress(StressBand.CALM), hour = 23)
        assertEquals(AmbientTrack.NIGHT, calmPlan.track)
    }

    @Test
    fun `白天平静时也能听雨`() {
        assertEquals(AmbientTrack.RAIN, MusicSuggestion.plan(stress(StressBand.CALM), hour = 14).track)
    }

    @Test
    fun `推荐语说得出放哪一段`() {
        val line = MusicSuggestion.line(Emotion.TENSE, AmbientTrack.BREATH)
        assertTrue(line.contains(AmbientTrack.BREATH.title))
        assertTrue(line.length in 8..40)
        // 不卖萌、不喊"宝贝"、不堆感叹号
        assertFalse(line.contains("宝贝"))
        assertFalse(line.contains("！！"))
    }
}