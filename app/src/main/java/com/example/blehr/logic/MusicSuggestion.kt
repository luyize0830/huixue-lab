package com.example.blehr.logic

/**
 * 内置的三段音景。
 *
 * 为什么不是"歌"：我们发不了有版权的音乐，也不该在比赛作品里塞盗版音频。
 * 这三段是 App 在本机实时合成的（正弦堆叠 + 滤波噪声），所以：
 * 离线可用、零版权风险、包体不增加。
 */
enum class AmbientTrack(
    val id: String,
    val title: String,
    val hint: String,
    /** 建议时长（分钟），到点会提醒，不是强制停止 */
    val minutes: Int
) {
    RAIN("rain", "雨落在窗台", "低音量，像有人在隔壁房间", 20),
    NIGHT("night", "夜里的风", "慢一点，适合收尾", 15),
    BREATH("breath", "慢慢呼吸", "跟着它吸气四秒、呼气六秒", 10)
}

data class MusicPlan(
    val track: AmbientTrack,
    /** 小夜会说的那句话 */
    val line: String
)

/**
 * 什么时候该推音乐、推哪一段、该说什么。
 *
 * 规则是先写死的（和 Initiative 一样），因为"推荐音乐"做不好就是打扰：
 * - 只有真的偏紧（HIGH），或者半夜还在用手机且有点紧（MILD）才推；
 * - 90 分钟内不重复推；
 * - 关掉开关就彻底不推（开关在「我的 → 音乐」）。
 */
object MusicSuggestion {

    /** 两次推荐之间至少隔 90 分钟 */
    const val COOLDOWN_MS = 90 * 60 * 1000L

    /** 半夜这个区间，只在用户明显醒着（屏幕在亮）时才推 */
    const val NIGHT_FROM = 23
    const val NIGHT_TO = 5

    fun isNight(hour: Int): Boolean = hour >= NIGHT_FROM || hour < NIGHT_TO

    fun shouldSuggest(
        stress: StressResult,
        hour: Int,
        screenMinutesLastHour: Int,
        lastSuggestedAt: Long,
        now: Long,
        enabled: Boolean = true
    ): Boolean {
        if (!enabled) return false
        if (stress.band == StressBand.UNKNOWN) return false
        if (lastSuggestedAt > 0L && now - lastSuggestedAt < COOLDOWN_MS) return false

        // 半夜：她在·但只在用户明显还醒着的时候才开口（屏幕在亮 = 醒着）
        if (isNight(hour)) {
            return screenMinutesLastHour >= 10 && stress.band != StressBand.CALM
        }
        return stress.band == StressBand.HIGH
    }

    /** 情绪决定放哪一段：绷着的先降速，累的直接收尾 */
    fun pickFor(emotion: Emotion): AmbientTrack = when (emotion) {
        Emotion.TENSE, Emotion.IRRITABLE -> AmbientTrack.BREATH
        Emotion.TIRED, Emotion.LOW -> AmbientTrack.NIGHT
        Emotion.CALM -> AmbientTrack.RAIN
    }

    fun plan(stress: StressResult, hour: Int): MusicPlan {
        val night = isNight(hour)
        val track = when {
            night && stress.emotion != Emotion.TENSE -> AmbientTrack.NIGHT
            else -> pickFor(stress.emotion)
        }
        return MusicPlan(track = track, line = line(stress.emotion, track))
    }

    /**
     * 她的话。语气照旧：短、不说教、不喊"宝贝"，不用感叹号堆情绪。
     */
    fun line(emotion: Emotion, track: AmbientTrack): String = when (emotion) {
        Emotion.TENSE -> "你现在有点绷。听会儿「${track.title}」，声音小一点就行。"
        Emotion.IRRITABLE -> "先别看了。放个「${track.title}」，跟着它慢下来。"
        Emotion.TIRED -> "别撑了。「${track.title}」，闭眼听完再说。"
        Emotion.LOW -> "不用说话。放首歌，我在这儿。"
        Emotion.CALM -> "这会儿还行。想安静的话，「${track.title}」挺合适。"
    }
}