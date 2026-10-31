package com.example.blehr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.ble.ScanHit
import kotlinx.coroutines.delay

/**
 * 单独的"搜手环"页。
 *
 * 为什么从首页的卡片里搬出来：附近设备一屏能有十几二十个，挤在首页的一个卡片里、
 * 还只露出一串 MAC 地址，用户根本找不到自己那只。这一页只做三件事——
 * 说清现在在干什么、把像手环的排到最前、给每一行一个明确的「连接」。
 */
@Composable
fun ScanScreen(
    hits: List<ScanHit>,
    hrStatus: String,
    heartRate: Int?,
    connectedName: String?,
    onStartScan: () -> Unit,
    onPick: (ScanHit) -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit
) {
    var onlyBands by remember { mutableStateOf(true) }
    var pending by remember { mutableStateOf<String?>(null) }
    var scanToken by remember { mutableStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf(15) }

    // 进页面先自动搜一次，用户不用再点一步
    LaunchedEffect(Unit) {
        if (hits.isEmpty() && !scanning) {
            remaining = 15
            scanning = true
            onStartScan()
        }
    }

    // 扫描倒计时：只用于界面提示，真正的停止由客户端内部计时器负责
    LaunchedEffect(scanToken) {
        if (scanToken == 0) return@LaunchedEffect
        scanning = true
        remaining = 15
        repeat(15) {
            delay(1000)
            remaining = 14 - it
        }
        scanning = false
    }

    // 心率一旦上来，说明连上了，把行上的"连接中"清掉
    LaunchedEffect(heartRate) {
        if (heartRate != null) pending = null
    }

    val shown = if (onlyBands) hits.filter { it.looksLikeBand } else hits
    val hiddenCount = hits.size - shown.size

    Column(Modifier.fillMaxSize().background(Look.Paper)) {
        BackRow(title = "连接手环", onBack = onBack)

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                // 已连接状态优先回答"现在到底连上没有"
                if (heartRate != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Look.Card)
                            .border(1.dp, Look.Hair, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Glyph(GlyphKind.PULSE, size = 22.dp, tint = Look.Olive)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "已连上 ${connectedName ?: "手环"}",
                                fontSize = 14.sp, color = Look.Ink, fontWeight = FontWeight.Medium
                            )
                            Text(
                                "正在读 $heartRate bpm · 关掉这页也会继续读",
                                fontSize = 12.sp, color = Look.InkSoft
                            )
                        }
                        Text(
                            "断开",
                            fontSize = 13.sp,
                            color = Look.Clay,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onDisconnect() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 状态 + 扫描进度 / 重新搜索
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Look.Card2)
                        .padding(16.dp)
                ) {
                    Text(hrStatus, fontSize = 13.sp, color = Look.Ink)
                    Spacer(Modifier.height(8.dp))
                    if (scanning) {
                        // 一条"正在扫"的进度感，比干等一句文字更可信
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .height(4.dp)
                                    .weight(1f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Look.Track)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((15 - remaining).coerceIn(0, 15) / 15f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Look.Olive)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("${remaining}s", fontSize = 12.sp, color = Look.InkSoft)
                        }
                    } else {
                        Text(
                            "再搜一次",
                            fontSize = 13.sp,
                            color = Look.Accent,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    scanToken++
                                    onStartScan()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("附近的设备", fontSize = 12.sp, color = Look.InkFaint)
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (onlyBands) Look.Accent.copy(alpha = 0.10f) else Look.Card2)
                            .clickable { onlyBands = !onlyBands }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (onlyBands) "只看像手环的（${hits.count { it.looksLikeBand }}）"
                            else "显示全部（${hits.size}）",
                            fontSize = 11.sp,
                            color = if (onlyBands) Look.Accent else Look.InkSoft
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (shown.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Look.Card)
                            .border(1.dp, Look.Hair, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            if (hits.isEmpty()) "还没有搜到设备" else "没有像手环的设备（还有 $hiddenCount 个其他设备）",
                            fontSize = 14.sp, color = Look.Ink, fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "手环要先开「心率广播」：小米运动健康 → 设备 → 心率广播（有的型号在运动模式里）。" +
                                "开完记得把小米运动健康切到后台，它占着连接的话这边连不上。",
                            fontSize = 12.sp, color = Look.InkSoft
                        )
                        if (hits.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "显示全部设备",
                                fontSize = 13.sp, color = Look.Accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onlyBands = false }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            items(shown, key = { it.address }) { hit ->
                DeviceRow(
                    hit = hit,
                    pending = pending == hit.address,
                    onPick = {
                        pending = hit.address
                        onPick(hit)
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    "连不上不用急：这个 App 没有手环也能用，手环只是让判断更准一点。",
                    fontSize = 11.sp, color = Look.InkFaint
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(hit: ScanHit, pending: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Look.Card)
            .border(1.dp, Look.Hair, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 信号三格：越强的格子越实
        Row(verticalAlignment = Alignment.Bottom) {
            val level = when {
                hit.rssi >= -55 -> 3
                hit.rssi >= -70 -> 2
                else -> 1
            }
            repeat(3) { i ->
                Box(
                    Modifier
                        .padding(end = 2.dp)
                        .width(3.dp)
                        .height((6 + i * 4).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < level) Look.Olive else Look.Track)
                )
            }
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hit.displayName,
                    fontSize = 14.sp,
                    color = Look.Ink,
                    fontWeight = FontWeight.Medium
                )
                if (hit.advertisedHr) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "心率广播",
                        fontSize = 10.sp,
                        color = Look.Olive,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Look.Olive.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "…${hit.tail} · ${hit.rssi} dBm" + if (hit.looksLikeBand) " · 像是手环" else "",
                fontSize = 11.sp, color = Look.InkFaint
            )
        }

        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (pending) Look.Card2 else Look.Accent.copy(alpha = 0.10f))
                .clickable(enabled = !pending) { onPick() }
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                if (pending) "连接中…" else "连接",
                fontSize = 13.sp,
                color = if (pending) Look.InkSoft else Look.Accent,
                fontWeight = FontWeight.Medium
            )
        }
    }
}