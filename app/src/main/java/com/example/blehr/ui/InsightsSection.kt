package com.example.blehr.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.logic.InsightsEngine
import com.example.blehr.logic.RecordCsv
import com.example.blehr.model.ActionItem
import com.example.blehr.model.ExperimentRecord
import com.example.blehr.notify.NudgeScheduler
import com.example.blehr.notify.Reminders

private val XCard = Look.Card2
private val XMint = Look.Olive
private val XAmber = Look.Wheat
private val XText = Look.Ink
private val XDim = Look.InkSoft

/** 把 CSV 丢进系统分享面板：用户可以发给自己、存进笔记，或者直接拖进电脑 */
fun shareCsv(ctx: Context, records: List<ExperimentRecord>, pool: List<ActionItem>) {
    val csv = "\uFEFF" + RecordCsv.toCsv(records, pool)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "回血实验室 · 实验记录导出")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    ctx.startActivity(Intent.createChooser(i, "导出我的记录"))
}

/**
 * 「回血图谱」的洞察区：不只是列平均值，而是回答三个问题——
 * 你什么时候最容易被掏空、哪种事对你真有效、心率到底降没降。
 */
@Composable
fun InsightsSection(records: List<ExperimentRecord>, pool: List<ActionItem>) {
    val ins = remember(records.size, pool.size) { InsightsEngine.compute(records, pool) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxWidth()) {
        Text(
            InsightsEngine.summaryLine(ins),
            color = XText,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(12.dp))

        // 时段画像
        ins.buckets.forEach { b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(b.label, color = XDim, fontSize = 11.sp, modifier = Modifier.width(108.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(XCard, RoundedCornerShape(3.dp))
                ) {
                    val f = if (b.avgRating == null) 0f else (b.avgRating!!.toFloat() / 5f).coerceIn(0.02f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth(f)
                            .height(6.dp)
                            .background(XAmber, RoundedCornerShape(3.dp))
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (b.count == 0) "—" else "${InsightsEngine.fmt(b.avgRating ?: 0.0)} 分 · ${b.count} 次",
                    color = XDim, fontSize = 11.sp
                )
            }
        }

        if (ins.top.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("对你最有效的三种", color = XText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            ins.top.forEachIndexed { i, s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${i + 1}", color = XMint, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(18.dp))
                    CategoryTag(s.action.category, size = 22.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(s.action.title, color = XText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        buildString {
                            append("${InsightsEngine.fmt(s.avgRating)} 分 · ${s.attempts} 次")
                            s.avgHrDelta?.let { d ->
                                append(" · 心率")
                                append(if (d <= -1) "↓${(-d).toInt()}" else if (d >= 1) "↑${d.toInt()}" else "持平")
                            }
                        },
                        color = XDim, fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ins.total > 0) {
                TextButton(onClick = { shareCsv(ctx, records, pool) }) {
                    Text("导出记录（CSV）", color = XMint, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            if (ins.total > 0 && ins.buckets.any { it.count > 0 }) {
                Text(
                    if (ins.hrDownRate > 0) "有心率记录的行动里 ${(ins.hrDownRate * 100).toInt()}% 心率下降"
                    else "还没有同时采到前后心率的记录",
                    color = XDim, fontSize = 10.sp
                )
            }
        }
    }
}

/** 主动提醒开关：打扰是特权，不是默认。用户可以一键关掉。 */
@Composable
fun NudgeCard(ctx: Context) {
    var on by remember { mutableStateOf(Reminders.isEnabled(ctx)) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("状态下滑时提醒我", color = XText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                if (on) "只在连续专注太久、久坐超过 1 小时或分数偏低时才发，两次至少隔 50 分钟"
                else "已关闭，我不会主动发通知",
                color = XDim, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
        Switch(
            checked = on,
            onCheckedChange = { v ->
                on = v
                Reminders.setEnabled(ctx, v)
                if (v) {
                    Reminders.ensureChannel(ctx)
                    NudgeScheduler.start(ctx)
                } else {
                    NudgeScheduler.stop(ctx)
                }
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Look.OnAccent,
                checkedTrackColor = XMint,
                uncheckedTrackColor = XCard
            )
        )
    }
}