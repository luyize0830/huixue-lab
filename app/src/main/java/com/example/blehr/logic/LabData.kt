package com.example.blehr.logic

import com.example.blehr.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SessionPhase { RUNNING, PAUSED, FEEDBACK }

data class ActionSession(
    val id: String,
    val action: ActionItem,
    val band: EnergyBand,
    val hrBefore: Int?,
    val startedAt: Long,
    val deadline: Long,
    val elapsedDeadline: Long,
    val boot: Int,
    val phase: SessionPhase = SessionPhase.RUNNING,
    val pausedSeconds: Int = 0,
    val paired: Boolean = false
) {
    fun remaining(now: Long, elapsed: Long, currentBoot: Int): Int {
        if (phase == SessionPhase.FEEDBACK) return 0
        if (phase == SessionPhase.PAUSED) return pausedSeconds
        val delta = if (boot >= 0 && boot == currentBoot) elapsedDeadline - elapsed else deadline - now
        return ((delta.coerceIn(0, action.minutes * 60_000L) + 999) / 1000).toInt()
    }

    fun pause(now: Long, elapsed: Long, currentBoot: Int): ActionSession =
        if (phase != SessionPhase.RUNNING) this else copy(
            phase = SessionPhase.PAUSED, pausedSeconds = remaining(now, elapsed, currentBoot))

    fun resume(now: Long, elapsed: Long, currentBoot: Int): ActionSession =
        if (phase != SessionPhase.PAUSED) this else copy(phase = SessionPhase.RUNNING,
            deadline = now + pausedSeconds * 1000L, elapsedDeadline = elapsed + pausedSeconds * 1000L,
            boot = currentBoot)

    companion object {
        fun start(action: ActionItem, band: EnergyBand, hr: Int?, now: Long, elapsed: Long,
                  boot: Int, pairStart: Long? = null): ActionSession {
            require(action.minutes in 1..10)
            val start = pairStart ?: now
            val left = ((start + action.minutes * 60_000L) - now).coerceIn(0, action.minutes * 60_000L)
            return ActionSession(UUID.randomUUID().toString(), action.copy(), band, hr, start,
                now + left, elapsed + left, boot, paired = pairStart != null)
        }
    }
}

data class LabData(
    val actions: List<ActionItem>,
    val records: List<ExperimentRecord> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val archived: Set<String> = emptySet(),
    val session: ActionSession? = null
) {
    fun withFeedback(rating: Int, hrAfter: Int?, now: Long): LabData {
        require(rating in 1..5)
        val s = session ?: return this
        require(s.phase == SessionPhase.FEEDBACK)
        val record = ExperimentRecord(s.action.id, s.band, s.hrBefore, hrAfter, rating, now, s.id, s.action)
        return copy(records = if (records.any { it.sessionId == s.id }) records else listOf(record) + records,
            session = null)
    }
}

/** Same codec for the atomic local snapshot and validated, versioned backups. */
object LabCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    private fun string(o: JSONObject, k: String, max: Int = 1000): String =
        (o.get(k) as? String)?.also { require(it.length <= max) { "文字字段过长" } }
            ?: error("备份文字字段不正确")
    private fun number(o: JSONObject, k: String): Long {
        val n = o.get(k)
        require(n is Int || n is Long) { "备份数字字段不正确" }
        return (n as Number).toLong()
    }
    private fun hr(o: JSONObject, k: String): Int? = if (o.isNull(k)) null else
        number(o, k).also { require(it in 1..300) { "心率字段不正确" } }.toInt()

    fun actionJson(a: ActionItem) = JSONObject().put("id", a.id).put("emoji", a.emoji)
        .put("title", a.title).put("minutes", a.minutes).put("category", a.category.name).put("cue", a.cue)

    fun action(o: JSONObject): ActionItem {
        val a = ActionItem(string(o, "id", 100), string(o, "emoji", 32), string(o, "title", 80),
            number(o, "minutes").also { require(it in 1..10) { "行动时长须为 1–10 分钟" } }.toInt(),
            ActionCategory.valueOf(string(o, "category", 20)), string(o, "cue", 500))
        require(a.id.isNotBlank() && a.title.isNotBlank()) { "行动名称不能为空" }
        return a
    }

    fun recordJson(r: ExperimentRecord) = JSONObject().put("actionId", r.actionId).put("band", r.band.name)
        .put("hrBefore", r.hrBefore ?: JSONObject.NULL).put("hrAfter", r.hrAfter ?: JSONObject.NULL)
        .put("rating", r.rating).put("ts", r.ts).put("sessionId", r.sessionId)
        .put("actionSnapshot", r.actionSnapshot?.let(::actionJson) ?: JSONObject.NULL)

    fun record(o: JSONObject): ExperimentRecord {
        val snap = if (o.isNull("actionSnapshot")) null else action(o.getJSONObject("actionSnapshot"))
        val id = string(o, "actionId", 100)
        require(id.isNotBlank() && (snap == null || snap.id == id))
        return ExperimentRecord(id, EnergyBand.valueOf(string(o, "band", 20)), hr(o, "hrBefore"),
            hr(o, "hrAfter"), number(o, "rating").also { require(it in 1..5) }.toInt(),
            number(o, "ts").also { require(it >= 0) }, string(o, "sessionId", 100), snap)
    }

    fun sessionJson(s: ActionSession) = JSONObject().put("id", s.id).put("action", actionJson(s.action))
        .put("band", s.band.name).put("hrBefore", s.hrBefore ?: JSONObject.NULL)
        .put("startedAt", s.startedAt).put("deadline", s.deadline).put("elapsedDeadline", s.elapsedDeadline)
        .put("boot", s.boot).put("phase", s.phase.name).put("pausedSeconds", s.pausedSeconds).put("paired", s.paired)

    fun session(o: JSONObject): ActionSession {
        val a = action(o.getJSONObject("action"))
        val paused = number(o, "pausedSeconds").also { require(it in 0..a.minutes * 60L) }.toInt()
        val started = number(o, "startedAt").also { require(it >= 0) }
        // A manual wall-clock correction during pause can make the new deadline precede the original start.
        val deadline = number(o, "deadline").also { require(it >= 0) }
        val elapsed = number(o, "elapsedDeadline").also { require(it >= 0) }
        val boot = number(o, "boot").also { require(it in -1..Int.MAX_VALUE.toLong()) }.toInt()
        val id = string(o, "id", 100).also { require(it.isNotBlank()) }
        require(o.get("paired") is Boolean)
        return ActionSession(id, a, EnergyBand.valueOf(string(o, "band", 20)), hr(o, "hrBefore"),
            started, deadline, elapsed, boot, SessionPhase.valueOf(string(o, "phase", 20)), paused, o.getBoolean("paired"))
    }

    fun encode(d: LabData): JSONObject = JSONObject()
        .put("actions", JSONArray(d.actions.map(::actionJson)))
        .put("records", JSONArray(d.records.map(::recordJson)))
        .put("favorites", JSONArray(d.favorites.toList())).put("archived", JSONArray(d.archived.toList()))
        .put("session", d.session?.let(::sessionJson) ?: JSONObject.NULL)

    fun decode(o: JSONObject): LabData {
        val aa = o.getJSONArray("actions"); val rr = o.getJSONArray("records")
        require(aa.length() <= 1000 && rr.length() <= 20000) { "备份条目过多" }
        val actions = (0 until aa.length()).map { action(aa.getJSONObject(it)) }
        require(actions.map { it.id }.distinct().size == actions.size) { "行动编号重复" }
        val ids = actions.map { it.id }.toSet()
        fun flags(key: String): Set<String> {
            val arr = o.getJSONArray(key)
            require(arr.length() <= actions.size)
            return (0 until arr.length()).map { (arr.get(it) as? String) ?: error("行动编号不正确") }
                .toSet().also { require(ids.containsAll(it)) { "收藏或归档引用不存在的行动" } }
        }
        val records = (0 until rr.length()).map { record(rr.getJSONObject(it)) }
        val sessions = records.map { it.sessionId }.filter { it.isNotEmpty() }
        require(sessions.distinct().size == sessions.size) { "实验记录重复" }
        val session = if (o.isNull("session")) null else session(o.getJSONObject("session"))
        require(session == null || session.id !in sessions) { "该会话已经记录过反馈" }
        return LabData(actions, records, flags("favorites"), flags("archived"), session)
    }
}
