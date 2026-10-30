package com.example.blehr.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * 手机侧行为信号：连续使用时长 + 近一小时前台时长。
 *
 * 数据来源：系统 UsageStatsManager（需要用户在"使用情况访问权限"里手动开启）。
 * 为什么不用 Health Connect：Health Connect 没有"屏幕时间"这类记录，屏幕使用只能从系统
 * 使用统计拿。所以两个信号各走各的通道，UI 上分别标注来源。
 *
 * 本类只负责"从系统取事件"，怎么算交给 [SessionMath]（纯逻辑、有单测）。
 */
data class UsageSnapshot(
    val available: Boolean,
    val sessionMinutes: Int,
    val foregroundMinutesLastHour: Int
)

object ScreenUsage {

    /** 只回看最近 6 小时的事件，足够判断"这一口气用了多久" */
    private const val LOOKBACK_MS = 6 * 60 * 60 * 1000L

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * 读取一次快照。没有权限时返回 available=false，调用方据此降级到手输/演示数据。
     *
     * 计算口径（写进文档时要说明清楚）：
     * - sessionMinutes：最近一次"熄屏或停用超过 5 分钟"之后到现在的时间；
     * - foregroundMinutesLastHour：最近 60 分钟内，有 Activity 处于前台的累计时长。
     */
    fun snapshot(ctx: Context, now: Long = System.currentTimeMillis()): UsageSnapshot {
        if (!hasAccess(ctx)) return UsageSnapshot(false, 0, 0)
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return UsageSnapshot(false, 0, 0)

        val events = usm.queryEvents(now - LOOKBACK_MS, now)
        val raw = UsageEvents.Event()
        val list = ArrayList<UsageEvent>(64)

        while (events.hasNextEvent()) {
            events.getNextEvent(raw)
            val type = when (raw.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> UsageEventType.SCREEN_ON
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventType.SCREEN_OFF
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.RESUME
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> UsageEventType.PAUSE
                else -> null
            } ?: continue
            list.add(UsageEvent(type, raw.timeStamp))
        }

        val result = SessionMath.compute(list, now)
        return UsageSnapshot(
            available = true,
            sessionMinutes = result.sessionMinutes,
            foregroundMinutesLastHour = result.foregroundMinutesLastHour
        )
    }
}