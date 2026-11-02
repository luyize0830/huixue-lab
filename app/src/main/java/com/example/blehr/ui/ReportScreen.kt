package com.example.blehr.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.logic.ExperimentReport
import com.example.blehr.logic.ReportData
import com.example.blehr.logic.ReportStat
import com.example.blehr.model.ActionItem
import com.example.blehr.model.ExperimentRecord

/**
 * 实验报告页。
 *
 * 它存在的理由很具体：答辩现场没人会翻你的 CSV，但会说"你说有效，凭什么"。
 * 这一页就是一页纸的答案 —— 次数、平均值、每类行动的对比，以及一句口径说明。
 */
@Composable
fun ReportScreen(
    records: List<ExperimentRecord>,
    pool: List<ActionItem>,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val data = ExperimentReport.build(records, pool)

    Column(
        Modifier
            .fillMaxSize()
            .background(Look.Paper)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← 返回", color = Look.InkSoft, fontSize = 13.sp) }
            Spacer(Modifier.weight(1f))
            Text("实验报告", color = Look.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(70.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "这些数字来自你自己的记录，不是模板。口径写在最下面。",
                color = Look.InkFaint,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))

            RCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigNum("${data.total}", "实验次数")
                    BigNum("${data.days}", "覆盖天数")
                    BigNum(ReportData.fmt(data.overallRating), "平均有效分")
                }
                Spacer(Modifier.height(10.dp))
                Text(data.headline, color = Look.Ink, fontSize = 13.sp, lineHeight = 20.sp)
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("按类别")
            RCard {
                if (data.byCategory.all { it.n == 0 }) {
                    EmptyHint()
                } else {
                    data.byCategory.forEach { s ->
                        BarRow(s, max = 5.0, value = s.avgRating)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("按时段")
            RCard {
                data.byHour.forEach { s ->
                    BarRow(s, max = 5.0, value = s.avgRating)
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("结论（只在这两个组都够样本时给）")
            RCard {
                if (data.best != null && data.worst != null && data.best != data.worst) {
                    Text(
                        "对你最有效：${data.best}（${ReportData.fmt(data.bestAvg)} 分）；" +
                            "最不管用：${data.worst}（${ReportData.fmt(data.worstAvg)} 分）。",
                        color = Look.Ink, fontSize = 13.sp, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "这只是你自己身上的现象，换个人可能相反。",
                        color = Look.InkFaint, fontSize = 11.sp
                    )
                } else {
                    Text(
                        "还不够。每组至少 ${ExperimentReport.MIN_GROUP} 次、总共 ${ReportData.RECOMMEND_MIN} 次以上，" +
                            "这里才会给排名 —— 试过一两次就说「哪种最有效」，是在骗自己。",
                        color = Look.InkSoft, fontSize = 12.sp, lineHeight = 19.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { shareReport(ctx, records, pool) }) {
                Text("导出这份报告（Markdown）", color = Look.Accent, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "口径：主观分自评 1-5；心率来自标准 BLE 心率服务实时值（不是手环历史）；" +
                    "每组少于 ${ExperimentReport.MIN_GROUP} 次只列不排名；N=1 不可外推；只描述现象，不做因果断言。",
                color = Look.InkFaint,
                fontSize = 10.5.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun RCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Look.Card, RoundedCornerShape(16.dp))
            .border(1.dp, Look.Hair, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Look.InkFaint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
    )
}

@Composable
private fun BigNum(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Look.Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Look.InkFaint, fontSize = 10.5.sp)
    }
}

/** 一条横向条：左边名字，中间条，右边"4.5 分 · 3 次" */
@Composable
private fun BarRow(s: ReportStat, max: Double, value: Double?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(s.label, color = Look.Ink, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(92.dp))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .background(Look.Track, RoundedCornerShape(4.dp))
        ) {
            val frac = if (value == null) 0f else (value / max).toFloat().coerceIn(0f, 1f)
            if (frac > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .height(8.dp)
                        .background(Look.Olive, RoundedCornerShape(4.dp))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (s.n == 0) "还没试过"
            else "${ReportData.fmt(s.avgRating)} 分 · ${s.n} 次",
            color = if (s.n == 0) Look.InkFaint else Look.InkSoft,
            fontSize = 11.sp,
            modifier = Modifier.width(76.dp)
        )
    }
}

@Composable
private fun EmptyHint() {
    Text(
        "还没有记录。做完一次实验回来打分，这里就会有第一条。",
        color = Look.InkFaint, fontSize = 12.sp, lineHeight = 18.sp
    )
}

private fun shareReport(ctx: android.content.Context, records: List<ExperimentRecord>, pool: List<ActionItem>) {
    val md = ExperimentReport.toMarkdown(records, pool)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "回血实验室 · 实验报告")
        putExtra(Intent.EXTRA_TEXT, md)
    }
    runCatching { ctx.startActivity(Intent.createChooser(send, "导出实验报告")) }
}