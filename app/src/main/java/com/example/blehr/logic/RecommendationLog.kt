package com.example.blehr.logic

import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ExperimentRecord
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class RecommendationOutcome { SHOWN, SELECTED, SKIPPED }

/** One displayed batch is one decision opportunity, regardless of recompositions or card count. */
data class RecommendationRound(
    val id: String,
    val shownAt: Long,
    val candidateIds: List<String>,
    val band: EnergyBand,
    val demo: Boolean,
    val outcome: RecommendationOutcome = RecommendationOutcome.SHOWN,
    val selectedActionId: String? = null,
    val sessionId: String? = null,
    val resolvedAt: Long? = null
)

data class RecommendationMetrics(val shown: Int, val selected: Int, val skipped: Int, val feedback: Int, val demo: Int) {
    val acceptance: Double? get() = if (shown == 0) null else selected * 100.0 / shown
    val feedbackRate: Double? get() = if (selected == 0) null else feedback * 100.0 / selected
}

object RecommendationLog {
    const val MAX_ROUNDS = 5000
    const val SCOPE = "仅统计首页推荐页；每批候选算一轮。演示轮次单列，不计入真实接受率；聊天和直接从回血池开始不计入。"

    fun show(rounds: List<RecommendationRound>, round: RecommendationRound): List<RecommendationRound> =
        if (rounds.any { it.id == round.id }) rounds else (rounds + round).takeLast(MAX_ROUNDS)

    fun select(rounds: List<RecommendationRound>, round: RecommendationRound, session: ActionSession, now: Long): List<RecommendationRound> {
        val existing = rounds.firstOrNull { it.id == round.id } ?: round
        require(existing.outcome == RecommendationOutcome.SHOWN && session.action.id in existing.candidateIds)
        return show(rounds, round).map { if (it.id != round.id) it else it.copy(
            outcome = RecommendationOutcome.SELECTED, selectedActionId = session.action.id,
            sessionId = session.id, resolvedAt = maxOf(now, it.shownAt)) }
    }

    fun skip(rounds: List<RecommendationRound>, id: String, now: Long): List<RecommendationRound> = rounds.map {
        if (it.id == id && it.outcome == RecommendationOutcome.SHOWN)
            it.copy(outcome = RecommendationOutcome.SKIPPED, resolvedAt = maxOf(now, it.shownAt)) else it
    }

    fun metrics(rounds: List<RecommendationRound>, records: List<ExperimentRecord>): RecommendationMetrics {
        val unique = rounds.distinctBy { it.id }
        val real = unique.filterNot { it.demo }
        val selected = real.filter { it.outcome == RecommendationOutcome.SELECTED }
        val completed = records.map { it.sessionId }.toSet()
        return RecommendationMetrics(real.size, selected.size, real.count { it.outcome == RecommendationOutcome.SKIPPED },
            selected.count { it.sessionId in completed }, unique.count { it.demo })
    }

    fun json(r: RecommendationRound): JSONObject = JSONObject().put("id", r.id).put("shownAt", r.shownAt)
        .put("candidateIds", JSONArray(r.candidateIds)).put("band", r.band.name).put("demo", r.demo)
        .put("outcome", r.outcome.name).put("selectedActionId", r.selectedActionId ?: JSONObject.NULL)
        .put("sessionId", r.sessionId ?: JSONObject.NULL).put("resolvedAt", r.resolvedAt ?: JSONObject.NULL)

    fun decode(o: JSONObject): RecommendationRound {
        fun text(key: String): String = (o.get(key) as? String)?.also { require(it.isNotBlank() && it.length <= 100) }
            ?: error("推荐记录字段不正确")
        fun time(key: String): Long {
            val value = o.get(key)
            require(value is Long || value is Int)
            return (value as Number).toLong().also { require(it >= 0) }
        }
        val ids = o.getJSONArray("candidateIds")
        require(ids.length() in 1..3 && o.get("demo") is Boolean)
        val candidates = (0 until ids.length()).map { i -> (ids.get(i) as? String)
            ?.also { require(it.isNotBlank() && it.length <= 100) } ?: error("推荐行动编号不正确") }
        require(candidates.distinct().size == candidates.size)
        val r = RecommendationRound(text("id"), time("shownAt"), candidates, EnergyBand.valueOf(text("band")),
            o.getBoolean("demo"), RecommendationOutcome.valueOf(text("outcome")),
            if (o.isNull("selectedActionId")) null else text("selectedActionId"),
            if (o.isNull("sessionId")) null else text("sessionId"),
            if (o.isNull("resolvedAt")) null else time("resolvedAt"))
        require(r.resolvedAt == null || r.resolvedAt >= r.shownAt)
        require(if (r.outcome == RecommendationOutcome.SELECTED)
            r.selectedActionId in candidates && r.sessionId != null && r.resolvedAt != null
        else r.selectedActionId == null && r.sessionId == null &&
            ((r.outcome == RecommendationOutcome.SHOWN) == (r.resolvedAt == null)))
        return r
    }

    fun percent(value: Double?): String = value?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"

    fun csv(rounds: List<RecommendationRound>, records: List<ExperimentRecord>): String {
        val completed = records.map { it.sessionId }.toSet()
        fun cell(value: Any?): String {
            val text = value.toString().let { if (it.firstOrNull() in listOf('=', '+', '-', '@')) "'$it" else it }
            return "\"" + text.replace("\"", "\"\"") + "\""
        }
        return "\uFEFFround_id,shown_at,source,band,candidate_ids,outcome,selected_action,session_id,resolved_at,feedback_saved\r\n" +
            rounds.joinToString("\r\n") { r -> listOf(r.id, r.shownAt, if (r.demo) "DEMO" else "DEVICE", r.band,
                r.candidateIds.joinToString("|"), r.outcome, r.selectedActionId.orEmpty(), r.sessionId.orEmpty(),
                r.resolvedAt ?: "", r.sessionId != null && r.sessionId in completed).joinToString(",", transform = ::cell) }
    }
}
