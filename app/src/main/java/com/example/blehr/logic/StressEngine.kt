package com.example.blehr.logic

/**
 * 压力与情绪推断。
 *
 * 先说边界，免得看起来像医疗器械：
 * - 压力是用**心率变异率（RMSSD）**推断的：副交感神经活跃度越低，波动越小，通常意味着"绷着"。
 * - 情绪是**推断**，不是测量：同样的 HRV 可能是紧张，也可能只是刚爬完楼。
 *   所以界面上一律写"看起来"、"推断"，并且把依据摊开给用户看。
 * - 没有手环数据时不做生理判断，只用行为线索（深夜、连续用手机），
 *   并把 [StressResult.measuredPhysiology] 标成 false —— 让界面能说"这只是按行为估的"。
 */
enum class StressBand(val label: String) {
    CALM("放松"),
    MILD("有点紧"),
    HIGH("偏紧"),
    UNKNOWN("还测不到")
}

enum class Emotion(val label: String) {
    CALM("平静"),
    TENSE("紧绷"),
    TIRED("累"),
    LOW("低落"),
    IRRITABLE("烦躁")
}

data class StressInput(
    /** 实时心率（手环广播） */
    val heartRate: Int? = null,
    /** 静息心率基线（取最近心率最低值近似） */
    val restingHr: Int? = null,
    /** 最近一段 R-R 间期算出的 RMSSD（毫秒），样本不足时为 null */
    val hrvRmssd: Double? = null,
    /** 连续专注分钟数 */
    val focusMinutes: Int = 0,
    /** 近一小时前台分钟数 */
    val screenMinutesLastHour: Int = 0,
    /** 0–23 */
    val hour: Int = 12,
    /** 状态分（StateEngine 的精力余额），可为空 */
    val energyScore: Int? = null
)

data class StressResult(
    /** 0–100，越高越紧 */
    val score: Int,
    val band: StressBand,
    val emotion: Emotion,
    /** 摊开来给用户看的依据，每条都是一句人话 */
    val reasons: List<String>,
    /** 有没有真实的生理数据参与 */
    val measuredPhysiology: Boolean
)

object StressEngine {

    /** RMSSD 低于这个值，认为波动很小 */
    const val HRV_VERY_LOW = 20.0
    const val HRV_LOW = 30.0
    const val HRV_OK = 45.0

    /** 心率比基线高出多少算"明显上来了" */
    const val HR_OVER_BASE_HIGH = 20
    const val HR_OVER_BASE_MID = 12

    const val SCORE_CALM_ABOVE = 35
    const val SCORE_HIGH_FROM = 60

    fun assess(input: StressInput): StressResult {
        val reasons = mutableListOf<String>()
        var penalty = 0

        val hr = input.heartRate
        val base = input.restingHr
        val rmssd = input.hrvRmssd

        val measured = rmssd != null || (hr != null && base != null)

        // 1) 心率变异率：这是最靠得住的一条
        if (rmssd != null) {
            when {
                rmssd < HRV_VERY_LOW -> {
                    penalty += 40
                    reasons.add("心率波动很小（RMSSD ${rmssd.round0()} ms），像是一直绷着")
                }
                rmssd < HRV_LOW -> {
                    penalty += 26
                    reasons.add("心率波动偏小（RMSSD ${rmssd.round0()} ms）")
                }
                rmssd < HRV_OK -> {
                    penalty += 12
                    reasons.add("心率波动一般（RMSSD ${rmssd.round0()} ms）")
                }
                else -> reasons.add("心率波动正常（RMSSD ${rmssd.round0()} ms）")
            }
        } else {
            reasons.add("没读到 R-R 间期，算不了心率变异率")
        }

        // 2) 心率比基线高多少
        if (hr != null && base != null && base > 0) {
            val delta = hr - base
            when {
                delta >= HR_OVER_BASE_HIGH -> {
                    penalty += 24
                    reasons.add("心率比平时高 ${delta} 次/分（$hr，基线 $base）")
                }
                delta >= HR_OVER_BASE_MID -> {
                    penalty += 14
                    reasons.add("心率比平时高 ${delta} 次/分")
                }
                delta >= 6 -> {
                    penalty += 6
                    reasons.add("心率略高于平时（+$delta）")
                }
                else -> Unit
            }
        }

        // 3) 行为线索：不占决定权，但能补上"没有手环"的场景
        if (input.focusMinutes >= 120) {
            penalty += 10
            reasons.add("连续专注 ${input.focusMinutes} 分钟没停")
        } else if (input.focusMinutes >= 90) {
            penalty += 6
            reasons.add("连续专注 ${input.focusMinutes} 分钟")
        }

        if (input.hour >= 23 || input.hour < 5) {
            penalty += 8
            reasons.add("这个点还没睡")
        }

        if (input.screenMinutesLastHour >= 50) {
            penalty += 6
            reasons.add("最近一小时屏幕亮了 ${input.screenMinutesLastHour} 分钟")
        }

        val score = (100 - penalty).coerceIn(0, 100)
        val stress = 100 - score // 转成"越高越紧"

        val band = when {
            !measured -> StressBand.UNKNOWN
            stress < SCORE_CALM_ABOVE -> StressBand.CALM
            stress < SCORE_HIGH_FROM -> StressBand.MILD
            else -> StressBand.HIGH
        }

        if (!measured) {
            reasons.add("手上没有手环数据，这只是按作息和行为估的")
        }

        return StressResult(
            score = stress,
            band = band,
            emotion = emotionOf(input, stress, measured),
            reasons = reasons,
            measuredPhysiology = measured
        )
    }

    /**
     * 情绪推断：先看生理，再看作息与精力，最后才用"其实都还好"兜底。
     * 顺序是刻意的——生理证据比"现在几点"更值钱。
     */
    private fun emotionOf(input: StressInput, stress: Int, measured: Boolean): Emotion {
        val hr = input.heartRate
        val base = input.restingHr
        val lateNight = input.hour >= 23 || input.hour < 5
        val energyLow = (input.energyScore ?: 100) < 50

        if (measured && hr != null && base != null && hr - base >= HR_OVER_BASE_MID && stress >= SCORE_HIGH_FROM) {
            return Emotion.TENSE
        }
        if (measured && stress >= SCORE_HIGH_FROM && input.screenMinutesLastHour >= 50) {
            return Emotion.IRRITABLE
        }
        if (lateNight && energyLow) return Emotion.TIRED
        if ((input.energyScore ?: 100) < 40) return Emotion.LOW
        if (stress >= SCORE_HIGH_FROM) return Emotion.TENSE
        return Emotion.CALM
    }

    private fun Double.round0(): Int = kotlin.math.round(this).toInt()
}