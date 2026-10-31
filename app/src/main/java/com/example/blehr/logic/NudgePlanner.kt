package com.example.blehr.logic

import com.example.blehr.model.StateAssessment

/**
 * 一条"要不要主动打扰你"的决策结果。
 *
 * 为什么把它做成纯逻辑：打扰人是有代价的——推错一次，用户就把通知关了。
 * 所以"什么时候该说话、说什么"必须是能写测试的确定逻辑，而不是散落在 UI 里的 if。
 */
data class Nudge(
    val title: String,
    val body: String,
    val actionId: String?
)

object NudgePlanner {

    /** 分数低于这个值才考虑打扰 */
    const val SCORE_THRESHOLD = 55

    /** 静坐超过这个分钟数才考虑提醒（没数据时不算） */
    const val SEDENTARY_THRESHOLD = 60

    /** 两次打扰之间至少隔这么久 */
    const val MIN_GAP_MS = 50 * 60 * 1000L

    /**
     * @param assessment 当前状态评估
     * @param sedentaryMinutes 久坐分钟数，null = 不知道（不编造）
     * @param topAction 当前最值得试的行动（可空）
     * @param lastNudgeAt 上次打扰时间戳，0 = 从没打扰过
     * @param now 当前时间
     * @return null 表示"这次不该说话"
     */
    fun plan(
        assessment: StateAssessment,
        sedentaryMinutes: Int?,
        topAction: Pair<String, String>?,   // (actionId, title)
        lastNudgeAt: Long,
        now: Long,
        enabled: Boolean = true
    ): Nudge? {
        if (!enabled) return null
        if (lastNudgeAt > 0 && now - lastNudgeAt < MIN_GAP_MS) return null

        val lowEnergy = assessment.score < SCORE_THRESHOLD
        val sitting = sedentaryMinutes != null && sedentaryMinutes >= SEDENTARY_THRESHOLD
        if (!lowEnergy && !sitting) return null

        val actionLine = topAction?.let { "试试「${it.second}」" } ?: "起来走两步"

        return when {
            sitting && lowEnergy -> Nudge(
                title = "已经 ${sedentaryMinutes} 分钟没动了",
                body = "状态也在往下掉。$actionLine，5 分钟后就回来。",
                actionId = topAction?.first
            )
            sitting -> Nudge(
                title = "坐得有点久了",
                body = "起身接杯水，或者$actionLine。",
                actionId = topAction?.first
            )
            else -> Nudge(
                title = "现在 ${assessment.score} 分，${assessment.band.label}",
                body = "不一定非要硬撑。$actionLine，做完告诉我有没有用。",
                actionId = topAction?.first
            )
        }
    }
}