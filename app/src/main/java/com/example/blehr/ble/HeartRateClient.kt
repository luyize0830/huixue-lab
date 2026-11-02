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
import kotlin.math.roundToInt

/**
 * 一次心率通知解出来的内容。
 *
 * [bpm] 是心率；[rrMs] 是设备附带广播的 R-R 间期（毫秒，已从 1/1024 秒换算）。
 * 很多手环只发心率不发 R-R —— 空列表就是"这个设备不广播"，
 * 界面据此说明"只能看心率，算不了 HRV"，而不是假装有。
 */
data class HrFrame(val bpm: Int, val rrMs: List<Int>)

/**
 * 一次扫描命中的设备。
 *
 * 为什么不直接把 BluetoothDevice 丢给界面：附近几十个设备里，用户要找的是"我手上这只"。
 * 所以这里补三个能帮上忙的字段：信号强度、名字、以及它有没有在广播标准心率服务。
 */
data class ScanHit(
    val name: String?,
    val address: String,
    val rssi: Int,
    val advertisedHr: Boolean,
    val device: BluetoothDevice
) {
    /** 像是"能测心率的东西"：广告里有 0x180D，或者名字里带手环/表的常见词 */
    val looksLikeBand: Boolean
        get() {
            if (advertisedHr) return true
            val n = name?.lowercase() ?: return false
            return listOf(
                "mi band", "xiaomi", "amazfit", "band", "手环", "手表", "watch",
                "heart", "hr-", "polar", "garmin", "coros", "whoop", "hw-", "honor"
            ).any { n.contains(it) }
        }

    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "未命名设备"

    /** 只在界面上露地址后四位：既能对上号，又不用用户读一长串 MAC */
    val tail: String
        get() = address.takeLast(5)
}

/**
 * 标准 BLE 心率客户端（HRS）。
 *
 * 刻意不引入任何厂商 SDK：任何支持心率广播的手环/胸带都能用，
 * 这是"设备无关"架构的落地基础。
 */
class HeartRateClient(private val context: Context) {

    var onStatus: (String) -> Unit = {}
    var onHeartRate: (Int) -> Unit = {}

    /** 完整帧（含 R-R 间期）。只想要心率的话继续用 onHeartRate 就行 */
    var onFrame: (HrFrame) -> Unit = {}

    /** 扫描结果（已按"像不像手环" + 信号强弱排序） */
    var onDevices: (List<ScanHit>) -> Unit = {}

    /** 连上/断开时回报设备名，界面据此显示「已连上谁」 */
    var onConnectedName: (String?) -> Unit = {}

    private val manager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private val found = LinkedHashMap<String, ScanHit>()

    var connectedName: String? = null
        private set

    val isConnected: Boolean
        get() = gatt != null

    // ---------------- 扫描 ----------------

    @SuppressLint("MissingPermission")
    fun startScan(seconds: Long = 15_000) {
        val adapter = manager?.adapter
        if (adapter == null) { onStatus("这台设备没有蓝牙"); return }
        if (!adapter.isEnabled) { onStatus("蓝牙没开，先去系统里打开蓝牙"); return }

        found.clear()
        onDevices(emptyList())
        onStatus("正在搜索附近的设备…")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        adapter.bluetoothLeScanner?.startScan(null, settings, scanCallback)

        handler.postDelayed({
            runCatching { adapter.bluetoothLeScanner?.stopScan(scanCallback) }
            if (found.isEmpty()) {
                onStatus("没搜到设备。检查：手环是否开了心率广播、是否已被小米运动健康占用")
            } else {
                val bands = found.values.count { it.looksLikeBand }
                onStatus(
                    if (bands > 0) "搜到 ${found.size} 个设备，其中 $bands 个像是手环"
                    else "搜到 ${found.size} 个设备，没有明显像手环的——可能是没开广播"
                )
            }
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
            val prev = found[d.address]
            val advertised = result.scanRecord?.serviceUuids?.any { it.uuid == HRS_SERVICE } == true
            val name = runCatching { d.name }.getOrNull() ?: prev?.name
            found[d.address] = ScanHit(
                name = name,
                address = d.address,
                rssi = result.rssi,
                advertisedHr = advertised || (prev?.advertisedHr == true),
                device = d
            )
            emit()
        }

        override fun onScanFailed(errorCode: Int) {
            onStatus("搜索失败（errorCode=$errorCode）")
        }
    }

    /** 像手环的排前面，同组内按信号从强到弱 */
    private fun emit() {
        val list = found.values.sortedWith(
            compareByDescending<ScanHit> { it.looksLikeBand }.thenByDescending { it.rssi }
        )
        onDevices(list)
    }

    // ---------------- 连接 ----------------

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScan()
        runCatching { gatt?.close() }
        gatt = null

        val name = runCatching { device.name }.getOrNull() ?: device.address
        connectedName = null
        onConnectedName(null)
        onStatus("正在连接 $name …")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        connectedName = null
        onConnectedName(null)
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
                    connectedName = runCatching { g?.device?.name }.getOrNull()
                        ?: runCatching { g?.device?.address }.getOrNull()
                    onConnectedName(connectedName)
                    onStatus("已连接，正在找心率服务…")
                    g?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt = null
                    connectedName = null
                    onConnectedName(null)
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
        val frame = parseFrame(value) ?: return
        onHeartRate(frame.bpm)
        onFrame(frame)
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

        /**
         * 按 Bluetooth HRS（0x2A37）规范解一帧。
         *
         * 字节布局：flags(1) → 心率(1 或 2 字节，看 bit0) → [能量消耗(2)，看 bit3]
         * → 若干 R-R 间期(每个 2 字节小端，单位 1/1024 秒，看 bit4)。
         * 解析顺序写死在这里，是因为顺序错了不会报错，只会安静地算出错误的心率。
         */
        fun parseFrame(value: ByteArray?): HrFrame? {
            if (value == null || value.size < 2) return null
            val flags = value[0].toInt() and 0xFF
            var i = 1

            val bpm: Int
            if (flags and 0x01 != 0) {
                if (value.size < i + 2) return null
                bpm = (value[i].toInt() and 0xFF) or ((value[i + 1].toInt() and 0xFF) shl 8)
                i += 2
            } else {
                bpm = value[i].toInt() and 0xFF
                i += 1
            }

            // bit3 = 有能量消耗字段，占 2 字节，不读会把它当成 R-R 的前半截
            if (flags and 0x08 != 0) i += 2

            val rr = ArrayList<Int>(4)
            if (flags and 0x10 != 0) {
                while (i + 1 < value.size) {
                    val raw = (value[i].toInt() and 0xFF) or ((value[i + 1].toInt() and 0xFF) shl 8)
                    i += 2
                    if (raw > 0) rr.add((raw * 1000.0 / 1024.0).roundToInt())
                }
            }
            return HrFrame(bpm, rr)
        }

        /** 只要心率的旧入口，保留是为了不破坏既有调用与测试 */
        fun parseHeartRate(value: ByteArray?): Int? = parseFrame(value)?.bpm
    }
}