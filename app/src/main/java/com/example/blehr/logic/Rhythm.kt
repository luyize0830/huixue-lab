package com.example.blehr.logic

/**
 * 「她像不像真人」有一半不在提示词里，在**消息的节奏**里。
 *
 * 同步问答的形态本身就是假的：你发一句 → 转圈 → 一大段回复一次性出现。
 * 真人不是这样：先隔几秒（可能在忙、可能在想），然后一句一句往外蹦，
 * 条与条之间还停顿；深夜回得慢，白天回得快；你只说了两个字，她不会
 * 憋三秒再回你一段长文。
 *
 * 这一层把"何时发、发几条、隔多久"从模型手里拿走，交给确定性的规则。
 * 模型负责说什么，这里负责怎么说出去。纯逻辑，可单测。
 */
object Rhythm {

    /** 一条消息一条气泡的间隔：再快就不像打字了。 */
    private const val GAP_MIN = 600L
    private const val GAP_MAX = 1500L

    data class Plan(
        /** 第一条出现之前，界面上「正在输入」停留多久 */
        val firstDelayMs: Long,
        /** 条与条之间的停顿 */
        val gapMs: Long,
        /** 这次实际发几条（超过实际内容时按内容截断） */
        val bubbles: Int,
        /** 深夜/犯困时，回得慢这件事本身就构成"没精神"的语感 */
        val sleepy: Boolean
    )

    /**
     * @param parts      内容已经切好的条数（Humanizer.split 的结果）
     * @param userChars  对方这句话有多少个字
     * @param hour       当前小时
     * @param mood       她的情绪
     * @param roll       0..1 的随机数，测试时传固定值
     */
    fun plan(
        parts: Int,
        userChars: Int,
        hour: Int,
        mood: MascotMood,
        roll: Double = Math.random()
    ): Plan {
        val n = parts.coerceIn(0, Humanizer.MAX_BUBBLES)
        if (n == 0) return Plan(0L, 0L, 0, false)

        val lateNight = hour >= 23 || hour < 5
        val sleepy = lateNight || mood == MascotMood.IDLE && hour < 9

        // 基准：深夜慢一倍，白天快；对方只说几个字时不要憋着
        val base = when {
            lateNight -> 2600L
            hour in 6..8 -> 1800L
            else -> 1200L
        }
        val perChar = (userChars.coerceIn(0, 40) * 18L)
        // 担心的时候她会更急着回；欠揍的时候慢慢来（这里用 IDLE 近似"懒"）
        val moodFactor = when (mood) {
            MascotMood.WORRIED -> 0.75
            MascotMood.HAPPY -> 0.9
            MascotMood.IDLE -> 1.15
        }
        val jitter = 0.85 + roll * 0.3
        val first = ((base + perChar) * moodFactor * jitter).toLong().coerceIn(500L, 5200L)

        // 条与条之间：深夜停顿更久（"打一半放下了"）
        val gapSpan = GAP_MAX - GAP_MIN
        val gap = GAP_MIN + (gapSpan * (if (lateNight) 0.75 else 0.35) + roll * gapSpan * 0.2).toLong()

        return Plan(firstDelayMs = first, gapMs = gap, bubbles = n, sleepy = sleepy)
    }
}