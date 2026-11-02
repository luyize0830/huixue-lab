package com.example.blehr.data

import android.content.Context
import com.example.blehr.logic.ChatTurn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 对话的存储格式：一行一轮，`u\t时间戳\t内容`。
 * 为什么不用 JSON：这个文件要被单元测试直接读写，org.json 在 JVM 测试里会抛 "not mocked"。
 * 格式写死在这里，解析也在这里，两边一起改，别的地方不许自己拼。
 */
object ChatCodec {

    private const val SEP = '\t'

    fun encode(turns: List<ChatTurn>): String =
        turns.joinToString("\n") { t ->
            "${if (t.fromUser) "u" else "a"}$SEP${t.ts}$SEP${escape(t.text)}"
        }

    fun decode(raw: String?): List<ChatTurn> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val i1 = line.indexOf(SEP)
            if (i1 <= 0) return@mapNotNull null
            val i2 = line.indexOf(SEP, i1 + 1)
            if (i2 <= i1) return@mapNotNull null
            val flag = line.substring(0, i1)
            val ts = line.substring(i1 + 1, i2).toLongOrNull() ?: return@mapNotNull null
            val text = unescape(line.substring(i2 + 1))
            if (text.isEmpty()) return@mapNotNull null
            ChatTurn(fromUser = flag == "u", text = text, ts = ts)
        }
    }

    private fun escape(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2; continue }
                    't' -> { sb.append('\t'); i += 2; continue }
                    '\\' -> { sb.append('\\'); i += 2; continue }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}

/**
 * 她的记忆。只存"最近 N 轮"和"一句话摘要"，不存全量 ——
 * 一是手机存储不该无界增长，二是拼进提示词的只有几千字符，多了反而稀释主题。
 */
object ChatMemory {

    private const val PREFS = "huixue_chat"
    private const val KEY_TURNS = "turns_v1"
    private const val KEY_LAST_DAY = "last_talk_day_v1"
    private const val KEY_NOTE = "note_v1"

    const val MAX_TURNS = 40
    const val PROMPT_TURNS = 6

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): List<ChatTurn> =
        ChatCodec.decode(prefs(ctx).getString(KEY_TURNS, null))

    fun append(ctx: Context, turn: ChatTurn) {
        val kept = (load(ctx) + turn).takeLast(MAX_TURNS)
        prefs(ctx).edit().putString(KEY_TURNS, ChatCodec.encode(kept)).apply()
    }

    fun replace(ctx: Context, turns: List<ChatTurn>) {
        prefs(ctx).edit().putString(KEY_TURNS, ChatCodec.encode(turns.takeLast(MAX_TURNS))).apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_TURNS).remove(KEY_LAST_DAY).remove(KEY_NOTE).apply()
    }

    /** 上次说过话的那天，"2026-11-02" 这种；用来判断"今天第一次打开" */
    fun lastTalkDay(ctx: Context): String? = prefs(ctx).getString(KEY_LAST_DAY, null)

    fun markTalked(ctx: Context, now: Long) {
        prefs(ctx).edit().putString(KEY_LAST_DAY, dayKey(now)).apply()
    }

    /** 一句话摘要：她是靠这个"记得你昨天说在赶报告"的 */
    fun note(ctx: Context): String? = prefs(ctx).getString(KEY_NOTE, null)

    fun setNote(ctx: Context, note: String?) {
        prefs(ctx).edit().apply {
            if (note.isNullOrBlank()) remove(KEY_NOTE) else putString(KEY_NOTE, note.take(40))
        }.apply()
    }

    fun dayKey(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    fun dayKey(date: LocalDate): String = date.toString()

    /** 第几天（从开始记录那天算起，最少 1） */
    fun dayIndex(firstRecordTs: Long?, now: Long): Int {
        if (firstRecordTs == null || firstRecordTs <= 0) return 1
        val first = Instant.ofEpochMilli(firstRecordTs).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        return (today.toEpochDay() - first.toEpochDay()).toInt().coerceAtLeast(0) + 1
    }
}