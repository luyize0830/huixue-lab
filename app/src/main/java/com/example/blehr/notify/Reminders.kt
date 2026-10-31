package com.example.blehr.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.example.blehr.MainActivity
import com.example.blehr.logic.Nudge

/**
 * 主动提醒（JITAI 的"just-in-time"那一半）。
 *
 * 为什么用 AlarmManager 而不是 WorkManager：
 * 这里只需要"每半小时醒一次，看一眼要不要提醒"，用系统闹钟就够了，
 * 而且不想为了一个定时器再引一个依赖（比赛里也要讲清楚"为什么选这个技术"）。
 *
 * 打扰纪律：两次提醒至少隔 50 分钟，状态不差且没久坐就不打扰。
 */
object Reminders {

    const val CHANNEL_ID = "huixue_nudge"
    private const val PREFS = "huixue_nudge"
    private const val K_ENABLED = "enabled"
    private const val K_LAST_AT = "last_at"
    private const val K_TITLE = "pending_title"
    private const val K_BODY = "pending_body"

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(K_ENABLED, true)

    fun setEnabled(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_ENABLED, v).apply()
    }

    fun lastNudgeAt(ctx: Context): Long = prefs(ctx).getLong(K_LAST_AT, 0L)

    private fun setLastNudgeAt(ctx: Context, v: Long) {
        prefs(ctx).edit().putLong(K_LAST_AT, v).apply()
    }

    /** 把"下一次该说什么"存起来，广播接收器只负责读 + 发，不做业务判断 */
    fun stash(ctx: Context, nudge: Nudge?) {
        if (nudge == null) {
            prefs(ctx).edit().remove(K_TITLE).remove(K_BODY).apply()
        } else {
            prefs(ctx).edit().putString(K_TITLE, nudge.title).putString(K_BODY, nudge.body).apply()
        }
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "该歇一下了", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "状态下滑或坐太久时，给你一个五分钟的建议"
            setShowBadge(true)
        }
        nm.createNotificationChannel(ch)
    }

    fun post(ctx: Context, title: String, body: String) {
        ensureChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, 1001, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        } else {
            Notification.Builder(ctx)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        }
        nm.notify(2001, notif)
        setLastNudgeAt(ctx, System.currentTimeMillis())
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}

/** 系统闹钟到点后进来：读预置文案，过一遍"打扰纪律"，再发。 */
class NudgeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        val app = ctx.applicationContext
        if (!Reminders.isEnabled(app)) return
        if (!Reminders.canPost(app)) return
        val p = Reminders.prefs(app)
        val title = p.getString("pending_title", null) ?: return
        val body = p.getString("pending_body", null) ?: ""
        if (System.currentTimeMillis() - Reminders.lastNudgeAt(app) < 50 * 60 * 1000L) return
        Reminders.post(app, title, body)
    }
}

object NudgeScheduler {

    private const val REQUEST = 3001

    private fun pi(ctx: Context): PendingIntent {
        val i = Intent(ctx, NudgeReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx, REQUEST, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun start(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val first = SystemClock.elapsedRealtime() + 15 * 60 * 1000L
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, first, 30 * 60 * 1000L, pi(ctx))
    }

    fun stop(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pi(ctx))
    }
}