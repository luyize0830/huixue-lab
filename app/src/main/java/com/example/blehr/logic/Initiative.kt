package com.example.blehr.logic

/**
 * 她会不会主动来找你。
 *
 * 这是"像真人"最难的一层：真人不是每次都由你开口。他会隔几小时想起来问你一句，
 * 而且他想起你的时候，自己手上正干着别的事。
 *
 * 但主动找人也是最容易做成骚扰的一层，所以规则必须先写死、再谈语气：
 * - 深夜不吵（23:00–07:00）：这是底线，不是参数。
 * - 有话说的时候不叠话：已经有一条没读的，就不再加一条。
 * - 一天最多 3 次，两次之间至少隔 1 小时。
 * - 状态差的时候门槛放低（2 小时），状态好的时候放高（3 小时）——
 *   她不是定时任务，她是因为"觉得你不太对"才来的。
 */
object Initiative {

    /** 状态正常时，隔多久才会想起你 */
    const val MIN_GAP_MS = 3 * 60 * 60 * 1000L

    /** 状态偏低时，这个门槛降到 2 小时 */
    const val MIN_GAP_LOW_MS = 2 * 60 * 60 * 1000L

    /** 两次主动找人之间至少隔这么久 */
    const val OUTREACH_COOLDOWN_MS = 60 * 60 * 1000L

    /** 多久检查一次（别每 30 秒都重算一遍） */
    const val CHECK_GAP_MS = 20 * 60 * 1000L

    /** 一天最多主动几次 */
    const val DAILY_CAP = 3

    const val QUIET_FROM = 23
    const val QUIET_TO = 7

    /** 状态低于这个分数，认为"他今天不太对"，愿意更早开口 */
    const val LOW_STATE_BELOW = 70

    data class State(
        val now: Long,
        val lastTalkAt: Long?,
        val lastCheckAt: Long,
        val lastOutreachAt: Long,
        val countToday: Int,
        val hour: Int,
        val score: Int,
        val hasPending: Boolean
    )

    /** 23 点到次日 7 点之间不主动找人 */
    fun inQuietHours(hour: Int): Boolean = hour >= QUIET_FROM || hour < QUIET_TO

    fun shouldReachOut(s: State): Boolean {
        // 已经有一条没读的——不叠话
        if (s.hasPending) return false
        // 深夜不吵
        if (inQuietHours(s.hour)) return false
        // 一天最多三次
        if (s.countToday >= DAILY_CAP) return false
        // 检查频率
        if (s.now - s.lastCheckAt < CHECK_GAP_MS) return false
        // 两次主动之间至少一小时
        if (s.lastOutreachAt > 0L && s.now - s.lastOutreachAt < OUTREACH_COOLDOWN_MS) return false

        // 从来没聊过的，不算"想起你"——第一次见面该由她按点打招呼，不是主动上门
        val lastTalk = s.lastTalkAt ?: return false
        val gap = s.now - lastTalk
        val threshold = if (s.score < LOW_STATE_BELOW) MIN_GAP_LOW_MS else MIN_GAP_MS
        return gap >= threshold
    }

    /** 她自己的事：主动开口时先说自己，而不是一上来就查岗 */
    private val OWN_LIFE = listOf(
        "我刚在阳台站了会儿，风有点凉",
        "刚睡醒，头发还是乱的",
        "刚把杯子洗了，忽然想起你",
        "手机快没电了，趁还能用先问一句",
        "刚刷到一个视频，看完忘了笑点在哪儿",
        "在等水开，等着也是等着"
    )

    private fun opening(hour: Int): String = when (hour) {
        in 7..10 -> "早。"
        in 11..13 -> "中午了。"
        in 14..17 -> "下午这个点最难熬。"
        in 18..22 -> "晚上好。"
        else -> ""
    }

    /**
     * 生成她会说的那句话。
     *
     * @param ownLife 指定"她自己的事"的下标，测试用；-1 表示随机
     * @param roll 0..1 的随机数，测试用
     */
    fun line(
        hour: Int,
        mood: MascotMood,
        note: String?,
        ownLife: Int = -1,
        roll: Double = Math.random()
    ): String {
        // 状态差的时候，不绕弯子，也不加"她自己的事"——那会显得轻浮
        if (mood == MascotMood.WORRIED) {
            return "你这两天状态不太对。我没别的意思，就是想问一句——今天还行吗？"
        }
        val own = OWN_LIFE[
            if (ownLife >= 0) ownLife.coerceIn(0, OWN_LIFE.size - 1)
            else (roll.coerceIn(0.0, 0.9999) * OWN_LIFE.size).toInt().coerceIn(0, OWN_LIFE.size - 1)
        ]
        val tail = if (!note.isNullOrBlank()) "上次你说「${note.take(20)}」，后来呢？" else "在干嘛呢？"
        return "${opening(hour)}$own。$tail"
    }
}