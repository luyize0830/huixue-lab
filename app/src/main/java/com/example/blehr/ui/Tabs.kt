package com.example.blehr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.data.Repo
import com.example.blehr.logic.InsightsEngine
import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.ExperimentRecord

/**
 * 从参考产品（Hevy 的统计/动作库/计划/详情四屏）反推出来的规则，落到本工程里：
 *
 * 1. 底部固定四格标签 + 中央主按钮：任何一屏最多两次点击就能回到核心动作。
 * 2. 统计页先给 2×2 的大数字，再给细节图；数字只回答"我这周怎么样"。
 * 3. 列表页三件套：搜索框 → 两行筛选 chip（分类、时长）→ 卡片行（左标签、中标题、右箭头）。
 * 4. 详情页自上而下固定顺序：大图形 → 标签行 → 一句怎么做 → 主按钮 → 参数区 → 说明。
 * 5. 空态不糊弄：没数据时写"还没有…"+ 一个明确动作，而不是摆一个 0。
 */
enum class Tab { HOME, POOL, GRAPH, JOIN }

@Composable
fun BottomNav(
    current: Tab,
    onSelect: (Tab) -> Unit,
    onStart: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Look.Card)) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem("今天", GlyphKind.PULSE, current == Tab.HOME) { onSelect(Tab.HOME) }
            NavItem("回血池", GlyphKind.POOL, current == Tab.POOL) { onSelect(Tab.POOL) }

            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Look.Accent)
                        .clickable(onClick = onStart),
                    contentAlignment = Alignment.Center
                ) {
                    Glyph(GlyphKind.BOLT, size = 24.dp, tint = Look.OnAccent)
                }
            }

            NavItem("图谱", GlyphKind.CHART, current == Tab.GRAPH) { onSelect(Tab.GRAPH) }
            NavItem("陪练", GlyphKind.PEOPLE, current == Tab.JOIN) { onSelect(Tab.JOIN) }
        }
    }
}


@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    label: String,
    kind: GlyphKind,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Glyph(
            kind,
            size = 22.dp,
            tint = if (selected) Look.Accent else Look.InkFaint,
            bg = Look.Card
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = if (selected) Look.Accent else Look.InkFaint,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/** 分段控件：参考产品里用「时间 / 总量」这种切换承载"同一块数据的两种看法"。 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Look.Card2)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Look.Card else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    color = if (on) Look.Ink else Look.InkSoft,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

/** 大数字卡：只回答一个问题，不带图表，不做装饰。 */
@Composable
fun StatCard(value: String, label: String, hint: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Look.Card)
            .border(1.dp, Look.Hair, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label, fontSize = 12.sp, color = Look.InkSoft)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Look.Ink)
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(hint, fontSize = 11.sp, color = Look.InkFaint)
        }
    }
}

/** 12 周回血日历：一格一天，颜色只表示"做过几次"。 */
@Composable
fun Heatmap(records: List<ExperimentRecord>, weeks: Int = 12) {
    val dayMs = 24L * 60 * 60 * 1000
    val now = System.currentTimeMillis()
    val today = now - now % dayMs
    val counts = HashMap<Long, Int>()
    records.forEach { r ->
        val d = r.ts - r.ts % dayMs
        counts[d] = (counts[d] ?: 0) + 1
    }

    Column {
        repeat(7) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(weeks) { col ->
                    val dayIndex = (weeks - 1 - col) * 7 + (6 - row)
                    val day = today - dayIndex * dayMs
                    val n = counts[day] ?: 0
                    val alpha = when {
                        n <= 0 -> 0f
                        n == 1 -> 0.28f
                        n == 2 -> 0.5f
                        else -> 0.78f
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (n == 0) Look.Track else Look.Olive.copy(alpha = alpha)
                            )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("少", fontSize = 10.sp, color = Look.InkFaint)
            Spacer(Modifier.width(6.dp))
            listOf(0f, 0.28f, 0.5f, 0.78f).forEach { a ->
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (a == 0f) Look.Track else Look.Olive.copy(alpha = a))
                )
                Spacer(Modifier.width(4.dp))
            }
            Text("多", fontSize = 10.sp, color = Look.InkFaint)
        }
    }
}

/** 筛选小胶囊：一行里放得下，选中用实底。 */
@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Look.Accent else Look.Card)
            .border(1.dp, if (selected) Look.Accent else Look.Hair, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (selected) Look.OnAccent else Look.InkSoft,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

// ---------------- 回血池（列表 + 详情） ----------------

@Composable
fun PoolTab(onOpenDetail: (ActionItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<ActionCategory?>(null) }
    var dur by remember { mutableStateOf(0) } // 0 全部 / 1 1-2 分钟 / 2 3-4 分钟 / 3 5 分钟+

    val list = Repo.pool.filter { a ->
        val hitQuery = query.isBlank() || a.title.contains(query, ignoreCase = true) ||
            a.cue.contains(query, ignoreCase = true)
        val hitCat = cat == null || a.category == cat
        val hitDur = when (dur) {
            1 -> a.minutes <= 2
            2 -> a.minutes in 3..4
            3 -> a.minutes >= 5
            else -> true
        }
        hitQuery && hitCat && hitDur
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("回血池", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Look.Ink)
            Text("${Repo.pool.size} 件随时能做的小事", fontSize = 12.sp, color = Look.InkSoft)
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜一下：走路 / 发呆 / 喝水", fontSize = 13.sp, color = Look.InkFaint) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions.Default,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Look.Accent,
                    unfocusedBorderColor = Look.Hair,
                    focusedContainerColor = Look.Card,
                    unfocusedContainerColor = Look.Card,
                    cursorColor = Look.Accent,
                    focusedTextColor = Look.Ink,
                    unfocusedTextColor = Look.Ink
                )
            )
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("全部", cat == null) { cat = null }
                ActionCategory.values().forEach { c ->
                    FilterChip(c.label, cat == c) { cat = c }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("不限时长", "1-2 分钟", "3-4 分钟", "5 分钟+").forEachIndexed { i, label ->
                    FilterChip(label, dur == i) { dur = i }
                }
            }
            Spacer(Modifier.height(14.dp))

            if (list.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph(GlyphKind.POOL, size = 36.dp, tint = Look.InkFaint)
                    Spacer(Modifier.height(10.dp))
                    Text("没有完全符合的", fontSize = 14.sp, color = Look.InkSoft)
                    Text("把条件放宽一点，或者换一个词搜", fontSize = 12.sp, color = Look.InkFaint)
                }
            } else {
                list.forEach { a ->
                    PoolRow(a) { onOpenDetail(a) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PoolRow(a: ActionItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Look.Card)
            .border(1.dp, Look.Hair, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryTag(a.category, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, fontSize = 15.sp, color = Look.Ink)
            Text("${a.category.label} · ${a.minutes} 分钟", fontSize = 11.sp, color = Look.InkSoft)
        }
        Text("›", fontSize = 20.sp, color = Look.InkFaint)
    }
}

@Composable
fun ActionDetailScreen(
    action: ActionItem,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val mine = Repo.records.filter { it.actionId == action.id }
    val avg = if (mine.isEmpty()) null else mine.sumOf { it.rating }.toDouble() / mine.size
    val hrPairs = mine.filter { it.hrBefore != null && it.hrAfter != null }
    val avgDelta = if (hrPairs.isEmpty()) null
    else hrPairs.sumOf { it.hrAfter!! - it.hrBefore!! }.toDouble() / hrPairs.size

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        BackRow(action.title, onBack)
        Spacer(Modifier.height(10.dp))

        // 大图形位：参考产品这里放动作动画；我们没有素材，就放大号汉字贴纸，
        // 但保留同样的"先看图，再看文字"的节奏
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Look.Card2),
            contentAlignment = Alignment.Center
        ) {
            Text(
                action.category.tagChar(),
                fontSize = 64.sp,
                fontWeight = FontWeight.Medium,
                color = Look.Accent.copy(alpha = 0.75f)
            )
        }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniTag(action.category.label)
            MiniTag("${action.minutes} 分钟")
            MiniTag(if (action.category == ActionCategory.REST || action.category == ActionCategory.CALM) "深夜也能做" else "需要起身")
        }
        Spacer(Modifier.height(14.dp))

        Text(
            action.cue,
            fontSize = 15.sp,
            color = Look.Ink,
            lineHeight = 23.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Look.Card)
                .border(1.dp, Look.Hair, RoundedCornerShape(14.dp))
                .padding(16.dp)
        )
        Spacer(Modifier.height(12.dp))

        Text("上次之后", fontSize = 12.sp, color = Look.InkSoft)
        Spacer(Modifier.height(6.dp))
        if (mine.isEmpty()) {
            Text("这个还没试过。第一次做完，它才会进入你的图谱。", fontSize = 13.sp, color = Look.InkFaint)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    value = String.format("%.1f", avg ?: 0.0),
                    label = "平均有效分",
                    hint = "试过 ${mine.size} 次",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = avgDelta?.let { (if (it > 0) "+" else "") + String.format("%.0f", it) } ?: "—",
                    label = "心率变化",
                    hint = if (avgDelta == null) "没同时采到前后心率" else "bpm（平均）",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(18.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Look.Accent)
                .clickable(onClick = onStart),
            contentAlignment = Alignment.Center
        ) {
            Text("开始 ${action.minutes} 分钟", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Look.OnAccent)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "做完回来点一下「有没有用」，我按你的反馈决定下次还推不推它。",
            fontSize = 12.sp,
            color = Look.InkFaint,
            modifier = Modifier.fillMaxWidth(),
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun MiniTag(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Look.Card2)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.sp, color = Look.InkSoft)
    }
}

// ---------------- 图谱（统计） ----------------

@Composable
fun GraphTab() {
    var range by remember { mutableStateOf(0) } // 0 全部 / 1 近 30 天 / 2 近 7 天
    val records = Repo.records
    val filtered = remember(records.size, range) {
        val dayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        when (range) {
            1 -> records.filter { now - it.ts <= 30 * dayMs }
            2 -> records.filter { now - it.ts <= 7 * dayMs }
            else -> records
        }
    }

    val total = filtered.size
    val avg = if (total == 0) null else filtered.sumOf { it.rating }.toDouble() / total
    val usefulRate = if (total == 0) null else filtered.count { it.rating >= 4 } * 100 / total
    val hrDown = filtered.count { r -> r.hrBefore != null && r.hrAfter != null && r.hrAfter!! < r.hrBefore!! }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("回血图谱", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Look.Ink)
        Text("哪种最有效、哪个时段最管用", fontSize = 12.sp, color = Look.InkSoft)
        Spacer(Modifier.height(14.dp))

        Segmented(listOf("全部", "近 30 天", "近 7 天"), range) { range = it }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                value = total.toString(),
                label = "实验次数",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = avg?.let { String.format("%.1f", it) } ?: "—",
                label = "平均有效分",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                value = usefulRate?.let { "$it%" } ?: "—",
                label = "觉得有用的比例",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = if (hrDown == 0) "—" else hrDown.toString(),
                label = "心率下降的次数",
                hint = if (total == 0) null else "共 ${filtered.count { it.hrAfter != null }} 次采到心率",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(14.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Look.Card)
                .border(1.dp, Look.Hair, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text("回血日历", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Look.Ink)
            Text("最近 12 周，一格一天", fontSize = 11.sp, color = Look.InkFaint)
            Spacer(Modifier.height(10.dp))
            Heatmap(records)
        }
        Spacer(Modifier.height(14.dp))

        InsightsSection(records = filtered, pool = Repo.pool)
        Spacer(Modifier.height(24.dp))
    }
}