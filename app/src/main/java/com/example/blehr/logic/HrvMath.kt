package com.example.blehr.logic

import kotlin.math.sqrt

/**
 * 心率变异性（HRV）的纯计算层。
 *
 * 为什么值得做：心率只说明"现在跳多快"，HRV（相邻心跳间隔的波动）才说明
 * "自主神经现在是紧绷还是松弛"。同一个 70 bpm，可能是一个放松的人，
 * 也可能是一个硬撑的人 —— 只看心率分不出来，看 RMSSD 能分出来一点。
 *
 * 为什么全部是纯函数：这些数字会出现在报告里，必须可测、可复核，
 * 不能埋在 BLE 回调里靠"感觉对"。
 *
 * 口径说明（写进文档时要一起交代）：
 * - 只接受 300–2200 ms 的 R-R（≈27–200 bpm），超出认为是丢包/伪迹，直接丢；
 * - 少于 [MIN_SAMPLES] 个有效间隔时一律返回 null —— 宁可不给数字，
 *   也不拿 2 个点算出一个"看起来很科学"的指标；
 * - pNN50 用百分比（0–100），不是 0–1，界面上直接加 "%"。
 */
object HrvMath {

    /** 有效 R-R 的下限：300 ms ≈ 200 bpm，再短基本都是伪迹 */
    const val MIN_RR_MS = 300

    /** 有效 R-R 的上限：2200 ms ≈ 27 bpm */
    const val MAX_RR_MS = 2200

    /** 少于此样本数不出指标（约 10 秒的心跳） */
    const val MIN_SAMPLES = 8

    fun sanitize(rr: List<Int>): List<Int> = rr.filter { it in MIN_RR_MS..MAX_RR_MS }

    /**
     * RMSSD：相邻 R-R 差值的均方根，短时（10 秒~5 分钟）最常用的副交感指标。
     * 越高通常越放松。
     */
    fun rmssd(rr: List<Int>): Double? {
        val v = sanitize(rr)
        if (v.size < MIN_SAMPLES) return null
        var sum = 0.0
        for (i in 1 until v.size) {
            val d = (v[i] - v[i - 1]).toDouble()
            sum += d * d
        }
        return sqrt(sum / (v.size - 1))
    }

    /** SDNN：R-R 的标准差，反映整体波动。样本越短越不稳定，所以只在界面上做参考 */
    fun sdnn(rr: List<Int>): Double? {
        val v = sanitize(rr)
        if (v.size < MIN_SAMPLES) return null
        val mean = v.sum().toDouble() / v.size
        var sum = 0.0
        for (x in v) {
            val d = x - mean
            sum += d * d
        }
        return sqrt(sum / v.size)
    }

    /** pNN50：相邻差值绝对值 > 50 ms 的占比（百分比） */
    fun pnn50(rr: List<Int>): Double? {
        val v = sanitize(rr)
        if (v.size < MIN_SAMPLES) return null
        var hit = 0
        for (i in 1 until v.size) if (kotlin.math.abs(v[i] - v[i - 1]) > 50) hit++
        return hit * 100.0 / (v.size - 1)
    }

    /**
     * 把 RMSSD 说成人话。阈值来自常见的成人静息参考区间（20~70 ms 量级），
     * 只用于界面上的一句提示，不做医学解读。
     */
    fun plainLabel(rmssd: Double?): String? = when {
        rmssd == null -> null
        rmssd < 20 -> "波动偏小，可能还绷着"
        rmssd < 50 -> "波动正常"
        else -> "波动较大，比较松"
    }
}