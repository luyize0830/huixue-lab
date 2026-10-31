package com.example.blehr.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
        HealthConnectClient.getSdkStatus(ctx, "com.example.blehr")
    }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    fun isAvailable(ctx: Context): Boolean =
        status(ctx) == HealthConnectClient.SDK_AVAILABLE

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
        val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

        val steps = runCatching {
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(startOfToday),
                        Instant.ofEpochMilli(now)
                    )
                )
            )
            resp.records.sumOf { it.count }.toInt()
        }.onFailure { Log.w(TAG, "steps failed: ${it.message}") }.getOrNull()

        val sleep = runCatching {
            val from = Instant.ofEpochMilli(now - 36L * 60 * 60 * 1000)
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(from)
                )
            )
            // 取昨晚最长的那一段（午睡很短，不该算进"昨夜睡眠"）
            val longest = resp.records.maxByOrNull {
                Duration.between(it.startTime, it.endTime).toMinutes()
            } ?: return@runCatching null
            Duration.between(longest.startTime, longest.endTime).toMinutes().toInt()
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