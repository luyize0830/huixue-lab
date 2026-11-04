package com.example.blehr.logic

import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.ActionStat
import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ExperimentRecord
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 推荐时的外部约束。
 *
 * 为什么单独抽一个类：推荐器本来只关心"哪个行动对你有效"，但真实使用里有别的边界——
 * 你只剩 5 分钟、现在在图书馆不能出声、现在是凌晨只适合收尾。这些约束不该写死在
 * 采样逻辑里，否则以后每加一种场景就要改一次核心算法。
 */
data class RecommendationConstraints(
    /** 用户只肯花这么多分钟；null = 不限制 */
    val maxMinutes: Int? = null,
    /** 深夜模式：只推"能收尾"的行动（闭眼、静下来、吃点喝点） */
    val nightMode: Boolean = false,
    /** 已经推过、这轮不想要的 */
    val excludeIds: Set<String> = emptySet()
) {
    companion object {
        val NIGHT_FRIENDLY = setOf(ActionCategory.REST, ActionCategory.CALM, ActionCategory.FUEL)
    }
}

/** 一条推荐 + 它为什么被推荐（可解释性） */
data class Recommendation(
    val action: ActionItem,
    val reason: String,
    val sampled: Double,
    val attemptsInBand: Int
)

/**
 * 推荐器：多臂老虎机的 Thompson 采样。
 *
 * 为什么用它（技术深度章节要点）：
 * - 每个行动在"每个精力档位"下维护一个 Beta(α, β) 后验，成功 = 用户给了 4 分及以上；
 * - 采样而不是直接取平均，天然带探索：不确定性大的行动也有机会被试到；
 * - 样本少时后验很宽，会偏向"没试过的"；样本多且效果差时会自然被排下去；
 * - 全部端侧计算，可解释（每个行动都能说出"你试过几次、平均几分"）。
 */
object Recommender {

    fun recommend(
        band: EnergyBand,
        pool: List<ActionItem>,
        records: List<ExperimentRecord>,
        limit: Int = 3,
        constraints: RecommendationConstraints = RecommendationConstraints()
    ): List<Recommendation> {
        if (pool.isEmpty()) return emptyList()

        val base = pool.filter { action ->
            (constraints.maxMinutes == null || action.minutes <= constraints.maxMinutes) &&
                action.id !in constraints.excludeIds
        }
        if (base.isEmpty()) return emptyList()

        // 深夜：优先能"收尾"的行动；但候选不够时退回全量，不能因为规则让推荐变空
        val eligible = if (constraints.nightMode) {
            val night = base.filter { it.category in RecommendationConstraints.NIGHT_FRIENDLY }
            if (night.size >= limit) night else base
        } else base

        val scored = eligible.map { action ->
            val rel = records.filter { it.actionId == action.id && it.band == band }
            val n = rel.size
            val success = rel.count { it.rating >= 4 }
            val alpha = 1.0 + success
            val beta = 1.0 + (n - success)

            var value = sampleBeta(alpha, beta)

            // 探索加成：完全没试过的行动给一点额外机会，避免冷启动只推老几样
            if (n == 0) value += 0.18

            val avg = if (n > 0) rel.sumOf { it.rating }.toDouble() / n else 0.0
            val reason = when {
                n == 0 -> "你还没在这个状态下试过它"
                success > 0 && avg >= 4.0 -> "同样状态下你试过 $n 次，平均 ${fmt(avg)} 分"
                success > 0 -> "试过 $n 次，有效 $success 次"
                n >= 2 -> "试过 $n 次，效果一般——还愿意再给它一次机会吗"
                else -> "只试过 1 次，样本还太少"
            }

            Recommendation(action, reason, value, n)
        }

        return scored.sortedByDescending { it.sampled }.take(limit)
    }

    /** 聚合出"回血图谱"用的统计 */
    fun stats(pool: List<ActionItem>, records: List<ExperimentRecord>): List<ActionStat> {
        val historyPool = (records.mapNotNull { it.actionSnapshot } + pool).distinctBy { it.id }
        return historyPool.mapNotNull { action ->
            val rel = records.filter { it.actionId == action.id }
            if (rel.isEmpty()) return@mapNotNull null
            val deltas = rel.mapNotNull { r ->
                val b = r.hrBefore
                val a = r.hrAfter
                if (b != null && a != null) (a - b).toDouble() else null
            }
            ActionStat(
                action = action,
                attempts = rel.size,
                avgRating = rel.sumOf { it.rating }.toDouble() / rel.size,
                avgHrDelta = if (deltas.isEmpty()) null else deltas.sum() / deltas.size
            )
        }.sortedWith(compareByDescending<ActionStat> { it.avgRating }.thenByDescending { it.attempts })
    }

    private fun fmt(v: Double) = String.format("%.1f", v)

    // ---------------- Beta 采样（Marsaglia-Tsang） ----------------

    private fun sampleBeta(a: Double, b: Double): Double {
        val x = sampleGamma(a)
        val y = sampleGamma(b)
        val s = x + y
        return if (s <= 0.0) 0.5 else x / s
    }

    /** 要求 shape >= 1；本场景 α, β 均 >= 1 */
    private fun sampleGamma(shape: Double): Double {
        if (shape < 1.0) {
            val u = Math.random().coerceAtLeast(1e-12)
            return sampleGamma(shape + 1.0) * Math.pow(u, 1.0 / shape)
        }
        val d = shape - 1.0 / 3.0
        val c = 1.0 / sqrt(9.0 * d)
        while (true) {
            var x: Double
            var v: Double
            do {
                x = gaussian()
                v = 1.0 + c * x
            } while (v <= 0.0)
            v = v * v * v
            val u = Math.random().coerceAtLeast(1e-12)
            if (ln(u) < 0.5 * x * x + d - d * v + d * ln(v)) return d * v
        }
    }

    private fun gaussian(): Double {
        val u1 = Math.random().coerceAtLeast(1e-12)
        val u2 = Math.random()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }
}
