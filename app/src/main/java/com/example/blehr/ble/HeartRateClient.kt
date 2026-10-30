package com.example.blehr.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * 标准 BLE 心率客户端（HRS）。
 *
 * 刻意不引入任何厂商 SDK：任何支持心率广播的手环/胸带都能用，
 * 这是"设备无关"架构的落地基础。
 */
class HeartRateClient(private val context: Context) {

    var onStatus: (String) -> Unit = {}
    var onHeartRate: (Int) -> Unit = {}
    var onDeviceList: (List<BluetoothDevice>) -> Unit = {}

    private val manager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private val found = mutableListOf<BluetoothDevice>()

    val isConnected: Boolean
        get() = gatt != null

    // ---------------- 扫描 ----------------

    @SuppressLint("MissingPermission")
    fun startScan(seconds: Long = 15_000) {
        val adapter = manager?.adapter
        if (adapter == null) { onStatus("这台设备没有蓝牙"); return }
        if (!adapter.isEnabled) { onStatus("蓝牙没开"); return }

        found.clear()
        onDeviceList(emptyList())
        onStatus("正在搜索附近的设备…")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        adapter.bluetoothLeScanner?.startScan(null, settings, scanCallback)

        handler.postDelayed({
            adapter.bluetoothLeScanner?.stopScan(scanCallback)
            if (found.isEmpty()) onStatus("没搜到设备。检查：手环是否开了心率广播、是否已被小米运动健康占用")
            else onStatus("搜到 ${found.size} 个设备，点手环名字连接")
        }, seconds)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        runCatching { manager?.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val d = result?.device ?: return
            if (found.none { it.address == d.address }) {
                found.add(d)
                onDeviceList(found.toList())
            }
        }

        override fun onScanFailed(errorCode: Int) {
            onStatus("搜索失败（errorCode=$errorCode）")
        }
    }

    // ---------------- 连接 ----------------

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScan()
        gatt?.close()
        gatt = null

        val name = runCatching { device.name }.getOrNull() ?: device.address
        onStatus("正在连接 $name …")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        onStatus("已断开")
    }

    fun close() {
        disconnect()
        handler.removeCallbacksAndMessages(null)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt?, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onStatus("连接失败（status=$status）")
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    onStatus("已连接，正在找心率服务…")
                    g?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt = null
                    onStatus("连接已断开")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt?, status: Int) {
            val ch = g?.getService(HRS_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
            if (ch == null) {
                onStatus("这个设备没有标准心率服务（可能没开广播）")
                runCatching { g?.disconnect() }
                return
            }
            runCatching { g.setCharacteristicNotification(ch, true) }
            val descriptor = ch.getDescriptor(CCCD)
            if (descriptor == null) { onStatus("缺少 CCCD，无法订阅"); return }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
            onStatus("找到心率服务，正在订阅…")
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) onStatus("已连上，等心率数据…")
            else onStatus("订阅失败（status=$status）")
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleHeartRate(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            handleHeartRate(characteristic.value)
        }
    }

    private fun handleHeartRate(value: ByteArray?) {
        val bpm = parseHeartRate(value) ?: return
        onHeartRate(bpm)
    }

    companion object {
        private val HRS_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HR_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Android 12+ 只需要「附近的设备」两项权限。
         * manifest 中 BLUETOOTH_SCAN 已声明 neverForLocation，
         * ACCESS_FINE_LOCATION 只对 Android 11 及以下生效（maxSdkVersion=30）。
         */
        fun requiredPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        fun hasPermissions(context: Context): Boolean =
            requiredPermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

        /** flags bit0 = 0 → 心率占 1 字节；= 1 → 占 2 字节小端 */
        fun parseHeartRate(value: ByteArray?): Int? {
            if (value == null || value.size < 2) return null
            val flags = value[0].toInt()
            return if (flags and 0x01 == 0) {
                value[1].toInt() and 0xFF
            } else {
                if (value.size < 3) return null
                (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
            }
        }
    }
}