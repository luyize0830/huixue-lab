package com.example.blehr.logic

/**
 * 回不回、回多慢 —— 只有一个答案：**一定要回**，但可以慢一点。
 *
 * 参考 qq-bridge 的做法（`batchWindowMs = 8000`、`looksLikeUnfinished` 再多等 4 秒），
 * 但去掉了它「超过两小时默认不回」那一档：那是在群里装人用的，
 * 放在一个陪伴角色身上，就是让用户对着手机等一条永远不会来的消息。
 *
 * 所以这里的规则很窄：
 * 1. 对方可能还在打字（连发几条），先合并等一等，别每条都抢着回；
 * 2. 最后一句像半句话，就再多等一会儿；
 * 3. 等到上限也等不到下文 —— 照回，不许沉默。
 */
object ReplyGate {

    /** 连发消息的合并窗口：对方 8 秒内不再说话，就当她这轮说完了 */
    const val BATCH_MS = 8_000L

    /** 最后一句像是没说完，再多给 4 秒 */
    const val UNFINISHED_EXTRA_MS = 4_000L

    /** 等再久也不体面了：到点就回 */
    const val MAX_WAIT_MS = 14_000L

    private val FINISHED_TAIL = Regex("[。！？!?…～~）)」』\"”]+$")

    /** 结尾是这些虚词，通常还接着下句 —— 但要排除「好的」「算了」这种完整短句 */
    private val SOFT_TAIL = Regex(
        "(的|了|吗|呢|吧|啊|呀|嘛|是|在|把|被|让|给|和|但|就|都|还|又|也|很|太|最|更|不|没|有|这|那|我|你|再|然后|所以|因为|如果|虽然)$"
    )

    private val SOFT_PUNCT = Regex("[,，、；;：:—]$")

    private val COMPLETE_SHORT = setOf(
        "好的", "行了", "算了", "知道了", "可以了", "没事了", "走了", "睡了",
        "来了", "懂了", "明白了", "抱歉", "没事", "好吧", "行吧", "算了吧",
        "好", "行", "嗯", "哦", "嗯嗯", "哈哈"
    )

    /**
     * 这句是不是没说完。
     * 判据按顺序：空 → 不算；有终止标点 → 算说完；完整短句白名单 → 算说完；
     * 以软标点或虚词结尾 → 没说完。
     */
    fun looksUnfinished(text: String): Boolean {
        val s = text.trim()
        if (s.isEmpty()) return false
        if (FINISHED_TAIL.containsMatchIn(s)) return false
        if (COMPLETE_SHORT.contains(s)) return false
        if (SOFT_PUNCT.containsMatchIn(s)) return true
        return SOFT_TAIL.containsMatchIn(s)
    }

    /**
     * 这一批该等多久。
     * 没消息 → 0（不排延迟）；最后一条没说完 → 多加 4 秒；始终不超过上限。
     */
    fun waitMs(messages: List<String>): Long {
        val last = messages.lastOrNull()?.trim().orEmpty()
        if (last.isEmpty()) return 0L
        val base = BATCH_MS
        val extra = if (looksUnfinished(last)) UNFINISHED_EXTRA_MS else 0L
        return (base + extra).coerceAtMost(MAX_WAIT_MS)
    }

    /**
     * 把这一批合并成一次模型输入。
     * 单条就原样；多条用换行拼，让对方能看出是连发的几句。
     */
    fun merge(messages: List<String>): String {
        val list = messages.map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            list.isEmpty() -> ""
            list.size == 1 -> list.first()
            else -> list.joinToString("\n")
        }
    }

    /** 距离上一条消息多少分钟（用于时间感；没有上一条返回 null） */
    fun minutesSince(lastTs: Long?, now: Long): Int? {
        if (lastTs == null || lastTs <= 0L) return null
        val diff = now - lastTs
        if (diff < 0) return 0
        return (diff / 60_000L).toInt()
    }
}