package com.example.blehr

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.blehr.ble.HeartRateClient
import com.example.blehr.data.Repo
import com.example.blehr.ui.HuixueApp

class MainActivity : ComponentActivity() {

    private lateinit var client: HeartRateClient

    private var heartRate by mutableStateOf<Int?>(null)
    private var restingHr by mutableStateOf<Int?>(null)
    private var hrStatus by mutableStateOf("还没连手环 —— 不连也能用，只是少了客观信号")
    private var devices by mutableStateOf<List<BluetoothDevice>>(emptyList())

    private val hrSamples = mutableListOf<Int>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            client.startScan()
        } else {
            hrStatus = "权限被拒绝，无法搜索设备"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)

        client = HeartRateClient(this).also { c ->
            c.onStatus = { hrStatus = it }
            c.onDeviceList = { devices = it }
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
                onConnect = { requestPermissionsThenScan() },
                onPickDevice = { device -> client.connect(device) },
                onDisconnect = {
                    client.disconnect()
                    heartRate = null
                    restingHr = null
                    hrSamples.clear()
                }
            )
        }
    }

    private fun requestPermissionsThenScan() {
        if (HeartRateClient.hasPermissions(this)) client.startScan()
        else permissionLauncher.launch(HeartRateClient.requiredPermissions())
    }

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }
}