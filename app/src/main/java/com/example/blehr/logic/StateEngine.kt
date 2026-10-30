package com.example.blehr.logic

import com.example.blehr.model.EnergyBand
import com.example.blehr.model.SensorSnapshot
import com.example.blehr.model.StateAssessment

/**
 * 可解释的状态判断。
 *
 * 设计原则：
 * 1. 只做规则，不做黑箱模型 —— 每条结论都必须能对应到一条"依据"，用户点开就能看懂；
 * 2. 数据缺失不报错，降级处理并如实说明（对应赛题"数据诚实"要求）；
 * 3. 不做医学结论，只说"要不要现在回一点电"。
 */
object StateEngine {

    fun assess(s: SensorSnapshot): StateAssessment {
        val reasons = mutableListOf<String>()
        var penalty = 0

        // ---- 1. 心率相对静息基线的抬升 ----
        val hr = s.heartRate
        val base = s.restingHr
        if (hr != null && base != null) {
            val diff = hr - base
            when {
                diff >= 15 -> {
                    penalty += 22
                    reasons += "心率 $hr bpm，比你今天的静息基线（$base bpm）高 $diff bpm"
                }
                diff >= 8 -> {
                    penalty += 10
                    reasons += "心率略高于基线（$hr bpm / 基线 $base bpm）"
                }
                else -> reasons += "心率 $hr bpm，接近基线（$base bpm）"
            }
        } else if (hr != null) {
            reasons += "当前心率 $hr bpm（还没建立基线，仅供参考）"
        } else {
            reasons += "没连上手环，这次只用行为信号判断"
        }

        // ---- 2. 连续专注时长 ----
        when {
            s.focusMinutes >= 120 -> {
                penalty += 18
                reasons += "已经连续专注 ${s.focusMinutes} 分钟，中间没有断开过"
            }
            s.focusMinutes >= 90 -> {
                penalty += 12
                reasons += "连续专注 ${s.focusMinutes} 分钟"
            }
            s.focusMinutes >= 60 -> {
                penalty += 6
                reasons += "专注了 ${s.focusMinutes} 分钟"
            }
            s.focusMinutes > 0 -> reasons += "刚专注 ${s.focusMinutes} 分钟"
        }

        // ---- 3. 时段 ----
        val late = s.hourOfDay >= 23 || s.hourOfDay < 5
        if (late) {
            penalty += 15
            reasons += "现在是 ${s.hourOfDay} 点，这个时段硬撑的代价比白天大"
        }

        // ---- 4. 屏幕时间 ----
        if (s.screenMinutesLastHour >= 40) {
            penalty += 8
            reasons += "近一小时屏幕时间 ${s.screenMinutesLastHour} 分钟"
        }

        val score = (100 - penalty).coerceIn(5, 100)
        val band = when {
            score < 40 -> EnergyBand.LOW
            score < 70 -> EnergyBand.MID
            else -> EnergyBand.HIGH
        }

        val headline = when (band) {
            EnergyBand.LOW -> when {
                late -> "这个点还在硬撑，先回 10% 的电量"
                s.focusMinutes >= 90 -> "不是没毅力，是该换一口气了"
                else -> "电量见底了，别再用意志力顶"
            }
            EnergyBand.MID -> if (s.focusMinutes >= 90)
                "还能撑，但这时候补一小口最划算"
            else "还有余量，可以先不管"
            EnergyBand.HIGH -> "状态不错，继续就好"
        }

        if (reasons.isEmpty()) reasons += "各项信号都在正常范围"

        return StateAssessment(band = band, score = score, headline = headline, reasons = reasons)
    }
}