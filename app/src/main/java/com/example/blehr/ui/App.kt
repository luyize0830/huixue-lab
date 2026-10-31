package com.example.blehr.ui

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.example.blehr.data.ActivitySnapshot
import com.example.blehr.data.ActivityTracker
import com.example.blehr.data.HealthConnectSource
import com.example.blehr.data.HealthRepo
import com.example.blehr.data.Repo
import com.example.blehr.data.ScreenUsage
import com.example.blehr.data.UsageRepo
import com.example.blehr.data.WearableSnapshot
import com.example.blehr.logic.Recommender
import com.example.blehr.logic.RecommendationConstraints
import com.example.blehr.logic.StateEngine
import com.example.blehr.logic.NudgePlanner
import com.example.blehr.logic.Pairing
import com.example.blehr.notify.NudgeScheduler
import com.example.blehr.notify.Reminders
import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ExperimentRecord
import com.example.blehr.model.SensorSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

// ---------------- 配色 ----------------
private val Bg = Look.Paper
private val CardBg = Look.Card
private val CardBg2 = Look.Card2
private val Mint = Look.Olive
private val Amber = Look.Wheat
private val Rose = Look.Clay
private val TextMain = Look.Ink
private val TextDim = Look.InkSoft

sealed class Screen {
    object Home : Screen()
    object Recommend : Screen()
    object Chat : Screen()
    object Join : Screen()
    data class Timer(val action: ActionItem, val pairStartMs: Long? = null) : Screen()
    data class Feedback(val action: ActionItem, val hrBefore: Int?) : Screen()
    object Graph : Screen()
    object Pool : Screen()
}

@Composable
fun HuixueApp(
    heartRate: Int?,
    hrStatus: String,
    restingHr: Int?,
    devices: List<BluetoothDevice>,
    onConnect: () -> Unit,
    onPickDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRequestActivity: () -> Unit
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    // 系统返回键：在子页面里回首页，而不是直接把 App 关掉（答辩现场最容易踩到）
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    val ctx = LocalContext.current
    val usage = UsageRepo.snapshot
    val activity = ActivityTracker.snapshot
    val wearable = HealthRepo.snapshot
    val scope = rememberCoroutineScope()

    // Health Connect 的授权弹窗由系统承载，这里只负责发起、再读一次结果
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        scope.launch { HealthRepo.refresh(ctx) }
    }

    // 手输值：没授权时用它；答辩想直接演到"低电量"也可以手动切
    var manualFocus by remember { mutableStateOf(95) }
    var manualScreen by remember { mutableStateOf(25) }
    var forceDemo by remember { mutableStateOf(false) }
    var hour by remember { mutableStateOf(LocalTime.now().hour) }

    // 有真实信号就用真实的，否则退回手输，并在 UI 上标明来源
    val usingReal = usage.available && !forceDemo
    val focusMinutes = if (usingReal) usage.sessionMinutes else manualFocus
    val screenMinutes = if (usingReal) usage.foregroundMinutesLastHour else manualScreen

    LaunchedEffect(Unit) {
        while (true) {
            UsageRepo.refresh(ctx)
            ActivityTracker.refresh()
            HealthRepo.refresh(ctx)
            hour = LocalTime.now().hour
            delay(30_000L)
        }
    }

    val snapshot = SensorSnapshot(
        heartRate = heartRate,
        restingHr = restingHr,
        focusMinutes = focusMinutes,
        screenMinutesLastHour = screenMinutes,
        hourOfDay = hour,
        sedentaryMinutes = activity.stillMinutes
    )
    val assessment = StateEngine.assess(snapshot)

    // 决定"要不要主动说话"，并把文案预置好；真正的发送由系统闹钟触发的广播完成，
    // 这样用户不打开 App 时提醒也能按纪律发出去。
    LaunchedEffect(assessment.score, activity.stillMinutes, Repo.records.size) {
        val top = Recommender.recommend(assessment.band, Repo.pool, Repo.records, 1).firstOrNull()
        val nudge = NudgePlanner.plan(
            assessment = assessment,
            sedentaryMinutes = activity.stillMinutes,
            topAction = top?.let { it.action.id to it.action.title },
            lastNudgeAt = Reminders.lastNudgeAt(ctx),
            now = System.currentTimeMillis()
        )
        Reminders.stash(ctx, nudge)
        if (Reminders.isEnabled(ctx)) NudgeScheduler.start(ctx)
    }
    val accent = when (assessment.band) {
        EnergyBand.LOW -> Rose
        EnergyBand.MID -> Amber
        EnergyBand.HIGH -> Mint
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Look.Accent,
            background = Look.Paper,
            surface = Look.Card,
            onPrimary = Look.OnAccent,
            onSurface = Look.Ink
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            when (val s = screen) {
                is Screen.Home -> HomeScreen(
                    assessment = assessment,
                    accent = accent,
                    heartRate = heartRate,
                    hrStatus = hrStatus,
                    devices = devices,
                    totalExperiments = Repo.records.size,
                    onConnect = onConnect,
                    onPickDevice = onPickDevice,
                    onDisconnect = onDisconnect,
                    focusMinutes = focusMinutes,
                    onFocusChange = { manualFocus = it; forceDemo = true },
                    screenMinutes = screenMinutes,
                    onScreenMinutesChange = { manualScreen = it; forceDemo = true },
                    hour = hour,
                    onHourChange = { hour = it },
                    usingReal = usingReal,
                    usageAvailable = usage.available,
                    demoFocus = manualFocus,
                    demoScreen = manualScreen,
                    onOpenUsageSettings = { ctx.startActivity(ScreenUsage.settingsIntent()) },
                    activity = activity,
                    onRequestActivity = onRequestActivity,
                    onToggleDemo = { forceDemo = !forceDemo },
                    onNeedBreak = { screen = Screen.Recommend },
                    onOpenChat = { screen = Screen.Chat },
                    onOpenJoin = { screen = Screen.Join },
                    onOpenGraph = { screen = Screen.Graph },
                    onOpenPool = { screen = Screen.Pool },
                    wearable = wearable,
                    onRequestHealth = { healthLauncher.launch(HealthConnectSource.permissions) }
                )

                is Screen.Recommend -> RecommendScreen(
                    band = assessment.band,
                    pool = Repo.pool,
                    records = Repo.records,
                    hour = hour,
                    onPick = { action -> screen = Screen.Timer(action) },
                    onBack = { screen = Screen.Home }
                )

                is Screen.Chat -> ChatScreen(
                    band = assessment.band,
                    pool = Repo.pool,
                    records = Repo.records,
                    hour = hour,
                    onPick = { action -> screen = Screen.Timer(action) },
                    onBack = { screen = Screen.Home }
                )

                is Screen.Timer -> TimerScreen(
                    action = s.action,
                    pairStartMs = s.pairStartMs,
                    heartRate = heartRate,
                    onFinish = { before ->
                        screen = Screen.Feedback(s.action, before)
                    },
                    onCancel = { screen = Screen.Home }
                )

                is Screen.Join -> JoinScreen(
                    pool = Repo.pool,
                    onJoined = { action, startAt ->
                        screen = Screen.Timer(action, startAt)
                    },
                    onBack = { screen = Screen.Home }
                )

                is Screen.Feedback -> FeedbackScreen(
                    action = s.action,
                    hrBefore = s.hrBefore,
                    heartRate = heartRate,
                    band = assessment.band,
                    onSaved = { screen = Screen.Home },
                    onSkip = { screen = Screen.Home }
                )

                is Screen.Graph -> GraphScreen(onBack = { screen = Screen.Home })

                is Screen.Pool -> PoolScreen(onBack = { screen = Screen.Home }, onStart = { screen = Screen.Timer(it) })
            }
        }
    }
}

// ---------------- 通用小组件 ----------------

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Look.Hair, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Look.Card)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, height: Int = 8) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(Look.Track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun BandPill(text: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(color, 5.dp)
        Spacer(Modifier.width(5.dp))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** 分数用环形表盘，比一根横条更像"电量"，也省掉一个巨大的数字占位。 */
@Composable
private fun ScoreRing(score: Int, color: Color, size: Dp = 86.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.072f
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = Look.Track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color, startAngle = -90f, sweepAngle = 360f * (score / 100f), useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Look.Ink)
            Text("/ 100", fontSize = 10.sp, color = Look.InkFaint)
        }
    }
}
@Composable
private fun BackRow(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("← 返回", color = TextDim) }
        Spacer(Modifier.width(4.dp))
        Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------- 1. 首页：状态 ----------------

@Composable
private fun HomeScreen(
    assessment: com.example.blehr.model.StateAssessment,
    accent: Color,
    heartRate: Int?,
    hrStatus: String,
    devices: List<BluetoothDevice>,
    totalExperiments: Int,
    onConnect: () -> Unit,
    onPickDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    focusMinutes: Int,
    onFocusChange: (Int) -> Unit,
    screenMinutes: Int,
    onScreenMinutesChange: (Int) -> Unit,
    hour: Int,
    onHourChange: (Int) -> Unit,
    usingReal: Boolean,
    usageAvailable: Boolean,
    demoFocus: Int,
    demoScreen: Int,
    onOpenUsageSettings: () -> Unit,
    activity: ActivitySnapshot,
    onRequestActivity: () -> Unit,
    onToggleDemo: () -> Unit,
    onNeedBreak: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenJoin: () -> Unit,
    onOpenGraph: () -> Unit,
    onOpenPool: () -> Unit,
    wearable: WearableSnapshot,
    onRequestHealth: () -> Unit
) {
    var showReasons by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("回血实验室", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Look.Ink)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (totalExperiments == 0) "今天 · 还没开始第一次" else "今天 · 已完成 $totalExperiments 次",
                    fontSize = 12.sp, color = Look.InkFaint
                )
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (heartRate != null) Look.Olive.copy(alpha = 0.10f) else Look.Card2)
                    .border(
                        1.dp,
                        if (heartRate != null) Look.Olive.copy(alpha = 0.22f) else Look.Hair,
                        RoundedCornerShape(11.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val beat = heartRate != null
                Glyph(GlyphKind.PULSE, size = 14.dp, tint = if (beat) Look.Olive else Look.InkFaint)
                Spacer(Modifier.width(6.dp))
                Text(
                    heartRate?.let { "$it bpm" } ?: "未连手环",
                    color = if (beat) Look.Olive else Look.InkFaint,
                    fontSize = 12.sp,
                    fontWeight = if (beat) FontWeight.Medium else FontWeight.Normal
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Card(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Look.Hair, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Look.Card)
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("此刻精力余额", fontSize = 12.sp, color = Look.InkFaint, letterSpacing = 0.4.sp)
                    Spacer(Modifier.weight(1f))
                    BandPill(assessment.band.label, accent)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(score = assessment.score, color = accent)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(assessment.headline, fontSize = 15.sp, color = Look.Ink, lineHeight = 22.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (showReasons) "收起依据" else "这个分是怎么来的？",
                            fontSize = 12.sp, color = Look.Accent,
                            modifier = Modifier.clickable { showReasons = !showReasons }
                        )
                        if (showReasons) {
                            Spacer(Modifier.height(8.dp))
                            assessment.reasons.forEach {
                                Text("· $it", fontSize = 12.sp, color = Look.InkSoft, lineHeight = 18.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("只做状态粗判，不做任何医学结论。", fontSize = 11.sp, color = Look.InkFaint)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = onNeedBreak,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Look.Accent, contentColor = Look.OnAccent)
        ) {
            Text("撑不住了，找个 5 分钟能做完的", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Look.Hair, RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenChat)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Glyph(GlyphKind.CHAT, size = 16.dp, tint = Look.Accent)
            Spacer(Modifier.width(8.dp))
            Text("说不清也行，说一句就好", fontSize = 14.sp, color = Look.Accent)
        }

        Spacer(Modifier.height(18.dp))

        // 三个入口用图标块，不再靠一行灰色文字 + 箭头
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EntryTile(
                GlyphKind.POOL, "回血池", "26 件小事",
                Modifier.weight(1f)
            ) { onOpenPool() }
            EntryTile(
                GlyphKind.CHART, "回血图谱", "哪种最有效",
                Modifier.weight(1f)
            ) { onOpenGraph() }
            EntryTile(
                GlyphKind.PEOPLE, "陪练码", "同一秒开始",
                Modifier.weight(1f)
            ) { onOpenJoin() }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 主动提醒（打扰是特权，默认开着但可一键关） ----
        SectionCard {
            NudgeCard(androidx.compose.ui.platform.LocalContext.current)
        }

        Spacer(Modifier.height(16.dp))

        // ---- 手环连接 ----
        SectionCard {
            Text("手环（可选，有更好）", fontSize = 13.sp, color = TextDim)
            Spacer(Modifier.height(8.dp))
            Text(hrStatus, fontSize = 13.sp, color = TextMain)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onConnect) { Text("搜索设备", color = Mint) }
                if (heartRate != null) TextButton(onClick = onDisconnect) { Text("断开", color = TextDim) }
            }
            if (devices.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                devices.forEach { d ->
                    TextButton(onClick = { onPickDevice(d) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${runCatching { d.name }.getOrNull() ?: "未命名"}  ${d.address}",
                            color = TextDim, fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 手环历史数据（Health Connect，系统级数据源）----
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("手环历史（可选）", fontSize = 13.sp, color = TextDim)
                Spacer(Modifier.weight(1f))
                Chip(
                    when {
                        !wearable.available -> "不可用"
                        !wearable.authorized -> "未授权"
                        else -> "已接入"
                    },
                    when {
                        !wearable.available -> TextDim
                        !wearable.authorized -> Amber
                        else -> Mint
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                !wearable.available -> {
                    Text(
                        wearable.message ?: "这台手机没有 Health Connect",
                        fontSize = 13.sp, color = TextMain
                    )
                    Text(
                        "没有它也能用：手环实时心率走蓝牙，睡眠这类历史数据拿不到就不显示。",
                        fontSize = 11.sp, color = TextDim.copy(alpha = 0.75f)
                    )
                }

                !wearable.authorized -> {
                    Text("还没授权读步数和睡眠", fontSize = 13.sp, color = TextMain)
                    Text(
                        "数据来自系统 Health Connect（小米运动健康写进去的话就能读到），只读、只在本机用。",
                        fontSize = 11.sp, color = TextDim.copy(alpha = 0.75f)
                    )
                    TextButton(onClick = onRequestHealth) {
                        Text("去授权", color = Mint)
                    }
                }

                else -> {
                    val steps = wearable.stepsToday
                    val sleep = wearable.sleepMinutesLastNight
                    if (steps == null && sleep == null) {
                        Text(
                            wearable.message ?: "授权了，但还读不到数据",
                            fontSize = 13.sp, color = TextMain
                        )
                        Text(
                            "常见原因：小米运动健康没有把数据写进 Health Connect。这时候我们宁可空着，也不编一个睡眠时长。",
                            fontSize = 11.sp, color = TextDim.copy(alpha = 0.75f)
                        )
                    } else {
                        if (sleep != null) {
                            Text(
                                "昨夜睡了 ${sleep / 60} 小时 ${sleep % 60} 分",
                                fontSize = 14.sp, color = TextMain, fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (steps != null) {
                            Text("今天走了 $steps 步", fontSize = 14.sp, color = TextMain)
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            "来自 Health Connect，只读。睡眠时长目前只用于展示，还没有进状态模型——不想拿没验证过的信号算分。",
                            fontSize = 11.sp, color = TextDim.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 信号来源（真实数据优先，可切演示）----
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("信号来源", fontSize = 13.sp, color = TextDim)
                Spacer(Modifier.weight(1f))
                Chip(if (usingReal) "真实数据" else "演示数据", if (usingReal) Mint else Amber)
            }
            Spacer(Modifier.height(10.dp))

            when {
                usingReal -> {
                    Text(
                        "手机使用情况：已授权",
                        fontSize = 13.sp, color = TextMain
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "连续使用 $focusMinutes 分钟 · 近 1 小时前台 $screenMinutes 分钟",
                        fontSize = 14.sp, color = TextMain, fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "来自系统使用统计（本机计算，不上传）。「连续使用」= 从上次熄屏或闲置 5 分钟后到现在。",
                        fontSize = 11.sp, color = TextDim.copy(alpha = 0.75f)
                    )
                }

                !usageAvailable -> {
                    Text("还没授权读取手机使用情况", fontSize = 13.sp, color = TextMain)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "现在用的是演示数据，授权后这栏会自动换成真实信号。",
                        fontSize = 12.sp, color = TextDim
                    )
                    TextButton(onClick = onOpenUsageSettings) {
                        Text("去授权（系统设置）", color = Mint)
                    }
                }

                else -> {
                    Text("当前使用演示数据", fontSize = 13.sp, color = TextMain)
                }
            }

            if (usageAvailable) {
                TextButton(onClick = onToggleDemo) {
                    Text(
                        if (usingReal) "改用演示数据" else "切回真实数据",
                        color = Mint, fontSize = 13.sp
                    )
                }
            }

            // ---- 身体活动（计步器）----
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Hair))
            Spacer(Modifier.height(10.dp))
            Text("身体活动", fontSize = 13.sp, color = TextDim)
            Spacer(Modifier.height(4.dp))
            when {
                !activity.available -> {
                    Text("还没授权读取身体活动", fontSize = 13.sp, color = TextMain)
                    Text(
                        "授权后能判断「你多久没起身走动了」。这个信号普通手机就能给，不依赖手环。",
                        fontSize = 11.sp, color = TextDim.copy(alpha = 0.75f)
                    )
                    TextButton(onClick = onRequestActivity) {
                        Text("去授权", color = Mint)
                    }
                }

                activity.stillMinutes == null -> {
                    Text("拿到了计步器，但还没观察到走动", fontSize = 13.sp, color = TextMain)
                    Text(
                        "走几步之后，这里会开始统计你坐了多久。",
                        fontSize = 11.sp, color = TextDim.copy(alpha = 0.75f)
                    )
                }

                else -> {
                    Text(
                        "已经 ${activity.stillMinutes} 分钟没起身走动 · 打开 App 后走了 ${activity.stepsSinceOpen} 步",
                        fontSize = 14.sp, color = TextMain, fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "来自手机计步器（本机计算，不上传）。久坐超过 60 分钟会影响精力判断。",
                        fontSize = 11.sp, color = TextDim.copy(alpha = 0.75f)
                    )
                }
            }

            if (!usingReal) {
                Spacer(Modifier.height(4.dp))
                Text("已连续使用 ${demoFocus} 分钟", fontSize = 13.sp, color = TextMain)
                Slider(
                    value = demoFocus.toFloat(),
                    onValueChange = { onFocusChange(it.roundToInt()) },
                    valueRange = 0f..180f
                )
                Text("近一小时屏幕 ${demoScreen} 分钟", fontSize = 13.sp, color = TextMain)
                Slider(
                    value = demoScreen.toFloat(),
                    onValueChange = { onScreenMinutesChange(it.roundToInt()) },
                    valueRange = 0f..90f
                )
                Text("当前时刻 ${hour} 点", fontSize = 13.sp, color = TextMain)
                Slider(
                    value = hour.toFloat(),
                    onValueChange = { onHourChange(it.roundToInt()) },
                    valueRange = 0f..23f
                )
                Text(
                    "演示数据仅用于答辩现场摆状态，报告中会标注哪些结论来自真实信号。",
                    fontSize = 11.sp, color = TextDim.copy(alpha = 0.75f)
                )
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

// ---------------- 2. 推荐 ----------------

@Composable
private fun RecommendScreen(
    band: EnergyBand,
    pool: List<ActionItem>,
    records: List<ExperimentRecord>,
    hour: Int,
    onPick: (ActionItem) -> Unit,
    onBack: () -> Unit
) {
    var batch by remember { mutableStateOf(0) }
    // 换一批时排除刚看过的那三个，否则"换"完还是同一批，用户会觉得没在听
    var excluded by remember { mutableStateOf(emptySet<String>()) }
    val lateNight = hour >= 23 

 hour < 5
    val recs = remember(band, records.size, pool.size, batch, hour) {
        Recommender.recommend(
            band, pool, records, limit = 3,
            constraints = RecommendationConstraints(
                nightMode = lateNight,
                excludeIds = excluded
            )
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("这个状态下，试一个？", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextMain)
        Text("选一件 5 分钟内能做完的，做完告诉我有用没用", fontSize = 13.sp, color = TextDim)
        Spacer(Modifier.height(18.dp))

        if (recs.isEmpty()) {
            Text("回血池是空的，先去「我的回血池」加几个你想做的事。", color = TextDim, fontSize = 14.sp)
        }

        recs.forEach { rec ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(1.dp, Look.Hair, RoundedCornerShape(16.dp))
                    .clickable { onPick(rec.action) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryTag(rec.action.category, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rec.action.title, fontSize = 17.sp, color = TextMain, fontWeight = FontWeight.Medium)
                            Text(rec.action.category.label, fontSize = 12.sp, color = TextDim)
                        }
                        Chip("${rec.action.minutes} 分钟", Mint)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("“${rec.action.cue}”", fontSize = 13.sp, color = TextMain.copy(alpha = 0.85f))
                    Spacer(Modifier.height(8.dp))
                    Text(rec.reason, fontSize = 12.sp, color = TextDim)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Button(
            onClick = {
                excluded = excluded + recs.map { it.action.id }
                batch++
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CardBg2, contentColor = TextMain)
        ) {
            Text("换一批")
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("不是现在，先算了", color = TextDim, fontSize = 13.sp)
        }
    }
}

// ---------------- 3. 计时 ----------------

@Composable
private fun TimerScreen(
    action: ActionItem,
    pairStartMs: Long?,
    heartRate: Int?,
    onFinish: (Int?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val total = action.minutes * 60
    var secondsLeft by remember {
        mutableStateOf(
            pairStartMs?.let { Pairing.remainingSeconds(action.minutes, it, System.currentTimeMillis()) }
                ?: total
        )
    }
    // 陪练码：什么时候点都能生成，用的都是"真正的开始时刻"，所以晚一分钟拉人也能对齐
    var pairCode by remember { mutableStateOf<String?>(null) }
    val hrAtStart = remember { heartRate }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
        vibrate(context, 900)
        onFinish(hrAtStart)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CategoryTag(action.category, size = 64.dp)
        Spacer(Modifier.height(12.dp))
        Text(action.title, fontSize = 20.sp, color = TextMain, fontWeight = FontWeight.Medium)
        Text(action.cue, fontSize = 13.sp, color = TextDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))

        Text(
            "%02d:%02d".format(secondsLeft / 60, secondsLeft % 60),
            fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Mint
        )
        Spacer(Modifier.height(18.dp))
        Bar(1f - secondsLeft.toFloat() / total, Mint, 10)
        Spacer(Modifier.height(14.dp))
        Text(
            heartRate?.let { "此刻心率 $it bpm" } ?: "没连手环，这次不记录心率",
            fontSize = 13.sp, color = TextDim
        )

        // ---- 陪练 ----
        Spacer(Modifier.height(18.dp))
        if (pairStartMs != null) {
            Text("陪练中 · 和对方同一秒开始，慢一点也没关系", fontSize = 12.sp, color = Amber)
        } else if (pairCode == null) {
            TextButton({
                val startAt = System.currentTimeMillis() - (total - secondsLeft) * 1000L
                pairCode = Pairing.encode(action, action.minutes, startAt)
            }) { Text("一个人没劲？拉个人一起 →", color = TextDim, fontSize = 12.sp) }
        } else {
            val code = pairCode!!
            SectionCard {
                Text("陪练码", fontSize = 12.sp, color = TextDim)
                Text(
                    Pairing.pretty(code),
                    fontSize = 24.sp, color = Mint, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "让朋友在「回血实验室 → 用陪练码加入」里输入，两边就同步开始、同步结束。",
                    fontSize = 11.sp, color = TextDim.copy(alpha = 0.8f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({
                        clipboard.setText(AnnotatedString(Pairing.pretty(code)))
                    }) { Text("复制", color = Mint, fontSize = 13.sp) }
                    TextButton({
                        val text = Pairing.shareText(action, action.minutes, Pairing.pretty(code))
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "把陪练码发给他"))
                    }) { Text("发给朋友", color = Mint, fontSize = 13.sp) }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                vibrate(context, 200)
                onFinish(hrAtStart)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("我好了，现在就结束")
        }
        TextButton(onClick = onCancel) { Text("取消这次实验", color = TextDim) }
    }
}

private fun vibrate(context: Context, ms: Long) {
    runCatching {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }
}

// ---------------- 4. 反馈 ----------------

@Composable
private fun FeedbackScreen(
    action: ActionItem,
    hrBefore: Int?,
    heartRate: Int?,
    band: EnergyBand,
    onSaved: () -> Unit,
    onSkip: () -> Unit
) {
    var rating by remember { mutableStateOf(0) }
    val delta = if (hrBefore != null && heartRate != null) heartRate - hrBefore else null

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text("刚才那一下，有用吗？", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextMain)
        Text("你的感觉比数据更权威——这是这次实验最重要的记录。", fontSize = 13.sp, color = TextDim)
        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val labels = listOf("没用", "一般", "还行", "有用", "很管用")
            labels.indices.forEach { i ->
                val selected = rating == i + 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Mint else CardBg)
                        .border(
                            1.dp,
                            if (selected) Mint else Look.Hair,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { rating = i + 1 }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        labels[i],
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected) Look.OnAccent else TextDim
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionCard {
            Text("本次实验记录", fontSize = 13.sp, color = TextDim)
            Spacer(Modifier.height(8.dp))
            Text("${action.title} · ${action.minutes} 分钟", fontSize = 15.sp, color = TextMain)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    delta == null -> "心率：未连接手环，本次只有主观反馈"
                    delta <= -5 -> "心率：$hrBefore → $heartRate bpm（下降 ${-delta}）"
                    delta >= 5 -> "心率：$hrBefore → $heartRate bpm（上升 $delta）"
                    else -> "心率：$hrBefore → $heartRate bpm（基本持平）"
                },
                fontSize = 13.sp, color = TextDim
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                Repo.addRecord(
                    ExperimentRecord(
                        actionId = action.id,
                        band = band,
                        hrBefore = hrBefore,
                        hrAfter = heartRate,
                        rating = rating,
                        ts = System.currentTimeMillis()
                    )
                )
                onSaved()
            },
            enabled = rating > 0,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (rating == 0) "先选一个感受" else "记下来", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("这次不记录", color = TextDim, fontSize = 13.sp)
        }
    }
}

// ---------------- 5. 回血图谱 ----------------

@Composable
private fun GraphScreen(onBack: () -> Unit) {
    val stats = remember(Repo.records.size, Repo.pool.size) {
        Recommender.stats(Repo.pool, Repo.records)
    }
    val records = Repo.records
    val avg = if (records.isEmpty()) 0.0 else records.sumOf { it.rating }.toDouble() / records.size
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackRow("回血图谱", onBack)
        Spacer(Modifier.height(12.dp))

        SectionCard {
            Text("你的规律", fontSize = 13.sp, color = TextDim)
            Spacer(Modifier.height(8.dp))
            InsightsSection(Repo.records, Repo.pool)
        }

        Spacer(Modifier.height(16.dp))

        SectionCard {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("${records.size}", fontSize = 30.sp, color = Mint, fontWeight = FontWeight.Bold)
                    Text("次实验", fontSize = 12.sp, color = TextDim)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (records.isEmpty()) "—" else String.format("%.1f", avg), fontSize = 30.sp, color = Mint, fontWeight = FontWeight.Bold)
                    Text("平均有效分", fontSize = 12.sp, color = TextDim)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "分值是你自己打的主观有效性（1-5）。样本还少时，别太当真。",
                fontSize = 11.sp, color = TextDim.copy(alpha = 0.8f)
            )
        }

        Spacer(Modifier.height(16.dp))

        if (stats.isEmpty()) {
            SectionCard {
                Text("还没有数据", fontSize = 15.sp, color = TextMain)
                Spacer(Modifier.height(6.dp))
                Text("做过一次实验之后，这里会出现「什么对你有效」的排序。", fontSize = 13.sp, color = TextDim)
            }
        } else {
            stats.forEach { st ->
                SectionCard(modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryTag(st.action.category, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(st.action.title, fontSize = 15.sp, color = TextMain)
                            Text(
                                "试过 ${st.attempts} 次" + (st.avgHrDelta?.let {
                                    " · 心率平均 ${if (it <= 0) "↓" else "↑"}${"%.0f".format(kotlin.math.abs(it))}"
                                } ?: ""),
                                fontSize = 12.sp, color = TextDim
                            )
                        }
                        Text(String.format("%.1f", st.avgRating), fontSize = 18.sp, color = Mint, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Bar((st.avgRating / 5.0).toFloat(), Mint, 8)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        if (confirmClear) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { Repo.clearRecords(); confirmClear = false }, modifier = Modifier.weight(1f)) {
                    Text("确认清空", color = Rose)
                }
                TextButton(onClick = { confirmClear = false }, modifier = Modifier.weight(1f)) {
                    Text("取消", color = TextDim)
                }
            }
        } else {
            TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                Text("清空所有记录", color = TextDim, fontSize = 13.sp)
            }
        }
        Text(
            "所有数据只存在这台手机上，不联网、不上传。",
            fontSize = 11.sp, color = TextDim.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(30.dp))
    }
}

// ---------------- 6. 回血池 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoolScreen(onBack: () -> Unit, onStart: (ActionItem) -> Unit) {
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(5) }
    var category by remember { mutableStateOf(ActionCategory.PLAY) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackRow("我的回血池", onBack)
        Spacer(Modifier.height(6.dp))
        Text(
            "写下你真正想做的事。推荐只会从这里挑——这是让「休息」变成「奖励」的关键。",
            fontSize = 13.sp, color = TextDim
        )
        Spacer(Modifier.height(16.dp))

        Repo.pool.forEach { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardBg)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryTag(a.category, size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, fontSize = 15.sp, color = TextMain)
                    Text("${a.category.label} · ${a.minutes} 分钟", fontSize = 11.sp, color = TextDim)
                }
                Text(
                    "开始",
                    fontSize = 13.sp,
                    color = Mint,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onStart(a) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Text(
                    "删",
                    fontSize = 13.sp,
                    color = TextDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { Repo.removeAction(a.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionCard {
            Text("加一个新的", fontSize = 14.sp, color = TextMain, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("做什么？例如：弹 5 分钟琴") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 10).forEach { m ->
                    val sel = minutes == m
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Mint.copy(alpha = 0.18f) else CardBg2)
                            .clickable { minutes = m }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("$m 分钟", color = if (sel) Mint else TextDim, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionCategory.values().forEach { c ->
                    val sel = category == c
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Mint.copy(alpha = 0.18f) else CardBg2)
                            .clickable { category = c }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(c.label, color = if (sel) Mint else TextDim, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val t = title.trim()
                    if (t.isNotEmpty()) {
                        Repo.addAction(
                            ActionItem(
                                id = "u_" + System.currentTimeMillis(),
                                emoji = "✨",
                                title = t,
                                minutes = minutes,
                                category = category,
                                cue = "就做这一件事，别的先不管"
                            )
                        )
                        title = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("加进回血池")
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}