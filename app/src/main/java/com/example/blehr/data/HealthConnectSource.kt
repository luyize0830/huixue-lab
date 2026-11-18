package com.example.blehr.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateRequest
import com.example.blehr.logic.SleepWindow
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId

/**
 * 手环历史数据（Health Connect）。
 *
 * 为什么先做这个而不是直接啃小米云 API：Health Connect 是 Android 的系统级数据入口，
 * 只要小米运动健康愿意往里写，我们就能用标准接口拿到步数和睡眠，不用逆向、不用登录态，
 * 也不需要用户把小米账号交出来。拿不到时如实说"这台手机没有这个数据源"，不编数。
 */
data class WearableSnapshot(
    val available: Boolean = false,
    val authorized: Boolean = false,
    val stepsToday: Int? = null,
    val sleepMinutesLastNight: Int? = null,
    val message: String? = null,
    val updatedAt: Long = 0L
)

object HealthConnectSource {

    private const val TAG = "HealthConnect"

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    fun status(ctx: Context): Int = runCatching {
        HealthConnectClient.getSdkStatus(ctx)
    }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    fun isAvailable(ctx: Context): Boolean =
        status(ctx) == HealthConnectClient.SDK_AVAILABLE

    /** 有没有装、要不要更新：界面要据此显示"去授权"还是"去安装"，否则点了没反应 */
    fun needsUpdate(ctx: Context): Boolean =
        status(ctx) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    /** 需要装/更新 Health Connect 时，跳到应用商店 */
    fun installIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=com.google.android.apps.healthdata")
    )

    /** 去系统里看/改已授予的健康数据权限 */
    fun settingsIntent(): Intent =
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    fun client(ctx: Context): HealthConnectClient? = runCatching {
        HealthConnectClient.getOrCreate(ctx)
    }.onFailure { Log.w(TAG, "getOrCreate failed: ${it.message}") }.getOrNull()

    suspend fun isAuthorized(ctx: Context): Boolean = runCatching {
        val c = client(ctx) ?: return false
        c.permissionController.getGrantedPermissions().containsAll(permissions)
    }.getOrDefault(false)

    /** 只读，不写回 Health Connect：我们不做数据搬运工，避免"顺手改了用户的健康档案"。 */
    suspend fun snapshot(ctx: Context, now: Long = System.currentTimeMillis()): WearableSnapshot {
        if (!isAvailable(ctx)) {
            return WearableSnapshot(
                available = false,
                message = "这台手机没有可用的 Health Connect"
            )
        }
        val c = client(ctx) ?: return WearableSnapshot(available = false, message = "Health Connect 打不开")
        if (!isAuthorized(ctx)) {
            return WearableSnapshot(available = true, authorized = false, message = "还没授权读手环历史数据")
        }

        val zone = ZoneId.systemDefault()
        val instant = Instant.ofEpochMilli(now)
        val startOfToday = instant.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        val steps = runCatching {
            val result = c.aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfToday, instant)))
            result[StepsRecord.COUNT_TOTAL]?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
        }.onFailure { Log.w(TAG, "steps failed: ${it.message}") }.getOrNull()

        val sleep = runCatching {
            val window = SleepWindow.range(instant, zone)
            val intervals = mutableListOf<SleepWindow.Interval>()
            var token: String? = null
            do {
                val resp = c.readRecords(ReadRecordsRequest(recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(window.start, window.end), pageToken = token))
                intervals += resp.records.map { SleepWindow.Interval(it.startTime, it.endTime) }
                token = resp.pageToken
            } while (token != null)
            SleepWindow.minutes(intervals, instant, zone)
        }.onFailure { Log.w(TAG, "sleep failed: ${it.message}") }.getOrNull()

        return WearableSnapshot(
            available = true,
            authorized = true,
            stepsToday = steps,
            sleepMinutesLastNight = sleep,
            message = when {
                steps == null && sleep == null -> "授权了，但 Health Connect 里没有小米写进来的数据"
                else -> null
            },
            updatedAt = now
        )
    }
}

/**
 * Compose 侧持有者。读取是 suspend 的，所以由 UI 的 LaunchedEffect 驱动，
 * 失败时只降级、不崩：拿不到就当没有，产品照常能用。
 */
object HealthRepo {

    var snapshot: WearableSnapshot by mutableStateOf(WearableSnapshot())
        private set

    suspend fun refresh(ctx: Context) {
        snapshot = HealthConnectSource.snapshot(ctx)
    }
}
