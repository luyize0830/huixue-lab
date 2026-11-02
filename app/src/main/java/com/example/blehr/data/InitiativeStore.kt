package com.example.blehr.data

import android.content.Context
import com.example.blehr.logic.Initiative
import com.example.blehr.logic.MoodRule
import com.example.blehr.notify.Reminders

/**
 * 主动找人的落盘与触发。
 *
 * 三件事：判断该不该开口（规则在 [Initiative]）、把要说的话存成"未读"、
 * 发一条通知。真正的"她说出口"发生在用户点开对话页那一刻——
 * 这样她的话一定是先说给用户听的，而不是先发出去再补一句。
 */
object InitiativeStore {

    private const val PREFS = "huixue_initiative"
    private const val KEY_CHECK = "last_check_v1"
    private const val KEY_OUTREACH = "last_outreach_v1"
    private const val KEY_COUNT_DAY = "count_day_v1"
    private const val KEY_COUNT = "count_v1"
    private const val KEY_PENDING = "pending_v1"
    private const val KEY_PENDING_TS = "pending_ts_v1"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 有没有一条"她先开口"但用户还没看到的话 */
    fun pending(ctx: Context): String? =
        prefs(ctx).getString(KEY_PENDING, null)?.takeIf { it.isNotBlank() }

    fun pendingAt(ctx: Context): Long = prefs(ctx).getLong(KEY_PENDING_TS, 0L)

    fun clearPending(ctx: Context) {
        prefs(ctx).edit().remove(KEY_PENDING).remove(KEY_PENDING_TS).apply()
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    /**
     * 检查一次要不要主动开口。
     *
     * @param notify 是否顺带发系统通知（用户关掉了提醒就不发，但站内那句仍然留着）
     * @return 这次开口说的话；没开口返回 null
     */
    fun maybeReach(ctx: Context, now: Long, hour: Int, score: Int, notify: Boolean): String? {
        val p = prefs(ctx)
        val today = ChatMemory.dayKey(now)
        val countDay = p.getString(KEY_COUNT_DAY, null)
        val count = if (countDay == today) p.getInt(KEY_COUNT, 0) else 0

        val state = Initiative.State(
            now = now,
            lastTalkAt = ChatMemory.load(ctx).lastOrNull()?.ts,
            lastCheckAt = p.getLong(KEY_CHECK, 0L),
            lastOutreachAt = p.getLong(KEY_OUTREACH, 0L),
            countToday = count,
            hour = hour,
            score = score,
            hasPending = pending(ctx) != null
        )

        if (!Initiative.shouldReachOut(state)) {
            p.edit().putLong(KEY_CHECK, now).apply()
            return null
        }

        val line = Initiative.line(
            hour = hour,
            mood = MoodRule.of(score),
            note = ChatMemory.note(ctx)
        )
        p.edit()
            .putLong(KEY_CHECK, now)
            .putLong(KEY_OUTREACH, now)
            .putString(KEY_COUNT_DAY, today)
            .putInt(KEY_COUNT, count + 1)
            .putString(KEY_PENDING, line)
            .putLong(KEY_PENDING_TS, now)
            .apply()

        if (notify && Reminders.isEnabled(ctx)) {
            Reminders.post(ctx, "小夜", line)
        }
        return line
    }
}