package com.example.blehr.data

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 身体活动信号：步数 + "已经多久没起身走动"。
 *
 * 为什么做这个：屏幕时间和心率只能说明"你在用手机/你心率偏高"，说不清"你是不是已经
 * 坐了很久"。久坐是大学生最普遍的疲劳来源之一，而且它能用手环外的普通手机传感器拿到，
 * 符合"设备无关"的产品前提。
 *
 * 数据来源：系统 TYPE_STEP_COUNTER（开机以来的累计步数），需要 ACTIVITY_RECOGNITION 权限。
 * 只要步数有变化就认为"动过"；两次变化之间的间隔就是久坐时长。
 */
data class ActivitySnapshot(
    val available: Boolean = false,
    val stepsSinceOpen: Int = 0,
    val stillMinutes: Int? = null
)

object ActivityTracker : SensorEventListener {

    const val PERMISSION = "android.permission.ACTIVITY_RECOGNITION"

    private const val PREFS = "huixue_activity"
    private const val KEY_LAST_MOVE = "last_move_at"

    var snapshot by mutableStateOf(ActivitySnapshot())
        private set

    private var manager: SensorManager? = null
    private var prefs: android.content.SharedPreferences? = null
    private var lastRaw = -1f
    private var baseRaw = -1f
    private var lastMoveAt = 0L

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** 需要 [hasPermission] 为 true；没有权限时只标记不可用，不抛异常 */
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lastMoveAt = prefs?.getLong(KEY_LAST_MOVE, 0L) ?: 0L

        if (!hasPermission(app)) {
            snapshot = ActivitySnapshot(available = false)
            return
        }
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sm == null || sensor == null) {
            // 少数设备没有计步器；此时如实标记不可用，由上层降级
            snapshot = ActivitySnapshot(available = false)
            return
        }
        manager = sm
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        snapshot = snapshot.copy(available = true)
        refresh()
    }

    fun stop() {
        manager?.unregisterListener(this)
        manager = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val v = event.values.getOrNull(0) ?: return
        if (baseRaw < 0f) baseRaw = v
        if (lastRaw >= 0f && v > lastRaw) {
            lastMoveAt = System.currentTimeMillis()
            prefs?.edit()?.putLong(KEY_LAST_MOVE, lastMoveAt)?.apply()
        }
        lastRaw = v
        refresh()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun refresh(now: Long = System.currentTimeMillis()) {
        if (!snapshot.available) return
        val steps = if (baseRaw >= 0f && lastRaw >= 0f) {
            (lastRaw - baseRaw).toInt().coerceAtLeast(0)
        } else 0
        snapshot = ActivitySnapshot(
            available = true,
            stepsSinceOpen = steps,
            stillMinutes = stillMinutes(lastMoveAt, now)
        )
    }

    /**
     * 纯逻辑：距上次"步数发生变化"过去了多少分钟。
     * 从没有过变化记录时返回 null —— 这代表"不知道"，而不是"一直没动"。
     */
    fun stillMinutes(lastMoveAt: Long, now: Long): Int? {
        if (lastMoveAt <= 0L) return null
        val diff = now - lastMoveAt
        if (diff < 0L) return null
        return (diff / 60_000L).toInt().coerceIn(0, 8 * 60)
    }
}