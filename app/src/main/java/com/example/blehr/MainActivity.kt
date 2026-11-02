package com.example.blehr

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.blehr.ble.HeartRateClient
import com.example.blehr.ble.HrFrame
import com.example.blehr.ble.ScanHit
import com.example.blehr.logic.HrvMath
import com.example.blehr.data.ActivityTracker
import com.example.blehr.data.Repo
import com.example.blehr.data.ProfileRepo
import com.example.blehr.data.UsageRepo
import com.example.blehr.notify.NudgeScheduler
import com.example.blehr.notify.Reminders
import com.example.blehr.ui.HuixueApp

class MainActivity : ComponentActivity() {

    private lateinit var client: HeartRateClient

    private var heartRate by mutableStateOf<Int?>(null)
    private var restingHr by mutableStateOf<Int?>(null)
    private var hrStatus by mutableStateOf("还没连手环 —— 不连也能用，只是少了客观信号")
    private var devices by mutableStateOf<List<ScanHit>>(emptyList())
    private var connectedName by mutableStateOf<String?>(null)
    private var hrvRmssd by mutableStateOf<Double?>(null)
    private var rrSupported by mutableStateOf(false)

    private val hrSamples = mutableListOf<Int>()

    /** 最近 2 分钟的 R-R 间期，用来算短时 HRV；样本太旧会让 RMSSD 失真，所以带窗口 */
    private val rrWindow = ArrayDeque<Int>()
    private var rrAtMs = 0L

    private fun onRrFrame(frame: HrFrame) {
        if (frame.rrMs.isEmpty()) return
        rrSupported = true
        val now = System.currentTimeMillis()
        rrWindow.addAll(frame.rrMs)
        rrAtMs = now
        while (rrWindow.size > 120) rrWindow.removeFirst()
        hrvRmssd = HrvMath.rmssd(rrWindow.toList())
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            client.startScan()
        } else {
            hrStatus = "权限被拒绝，无法搜索设备"
        }
    }

    private val activityLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ActivityTracker.start(this)
    }

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝也不影响主流程：主动提醒只是增强 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        ProfileRepo.init(this)
        Reminders.ensureChannel(this)

        // 计步器不需要用户主动触发，进 App 就开始观察；没有权限时内部自动标记不可用
        ActivityTracker.start(this)

        // 通知权限：Android 13+ 才需要单独申请，拒绝也能用（只是不发主动提醒）
        // 第一次打开的人先看到认脸页，等她上线之后再问 —— 别让系统弹窗盖在人设页上
        if (ProfileRepo.current.signedIn &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Reminders.canPost(this)
        ) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        client = HeartRateClient(this).also { c ->
            c.onStatus = { hrStatus = it }
            c.onDevices = { devices = it }
            c.onConnectedName = { connectedName = it }
            c.onFrame = { frame -> onRrFrame(frame) }
            c.onHeartRate = { bpm ->
                heartRate = bpm
                // 会话内最低心率作为"静息基线"的近似（正式版应由设备历史数据提供）
                hrSamples.add(bpm)
                if (hrSamples.size > 30) hrSamples.removeAt(0)
                restingHr = hrSamples.minOrNull()
            }
        }

        setContent {
            HuixueApp(
                heartRate = heartRate,
                hrStatus = hrStatus,
                restingHr = restingHr,
                devices = devices,
                connectedName = connectedName,
                hrvRmssd = hrvRmssd,
                rrSupported = rrSupported,
                onStartScan = { requestPermissionsThenScan() },
                onStopScan = { client.stopScan() },
                onPickDevice = { hit -> client.connect(hit.device) },
                onDisconnect = {
                    client.disconnect()
                    heartRate = null
                    restingHr = null
                    connectedName = null
                    hrSamples.clear()
                    rrWindow.clear()
                    hrvRmssd = null
                },
                onRequestActivity = {
                    if (ActivityTracker.hasPermission(this)) ActivityTracker.start(this)
                    else activityLauncher.launch(ActivityTracker.PERMISSION)
                },
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.canPost(this)) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }
    }

    private fun requestPermissionsThenScan() {
        if (HeartRateClient.hasPermissions(this)) client.startScan()
        else permissionLauncher.launch(HeartRateClient.requiredPermissions())
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置授权回来时，立刻刷新一次使用统计
        UsageRepo.refresh(this)
    }

    override fun onDestroy() {
        client.close()
        ActivityTracker.stop()
        super.onDestroy()
    }
}