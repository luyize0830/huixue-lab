package com.example.blehr.ui

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.data.ActivitySnapshot
import com.example.blehr.data.ActivityTracker
import com.example.blehr.data.Repo
import com.example.blehr.data.ScreenUsage
import com.example.blehr.data.UsageRepo
import com.example.blehr.logic.Recommender
import com.example.blehr.logic.StateEngine
import com.example.blehr.logic.NudgePlanner
import com.example.blehr.notify.NudgeScheduler
import com.example.blehr.notify.Reminders
import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ExperimentRecord
import com.example.blehr.model.SensorSnapshot
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.roundToInt

// ---------------- 配色 ----------------
private val Bg = Color(0xFF0D1117)
private val CardBg = Color(0xFF171D26)
private val CardBg2 = Color(0xFF1E2632)
private val Mint = Color(0xFF3EDDC1)
private val Amber = Color(0xFFFFC266)
private val Rose = Color(0xFFFF7B7B)
private val TextMain = Color(0xFFEDF2F7)
private val TextDim = Color(0xFF8B98A9)

sealed class Screen {
    object Home : Screen()
    object Recommend : Screen()
    object Chat : Screen()
    data class Timer(val action: ActionItem) : Screen()
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

    val ctx = LocalContext.current
    val usage = UsageRepo.snapshot
    val activity = ActivityTracker.snapshot

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
        colorScheme = darkColorScheme(
            primary = Mint,
            background = Bg,
            surface = CardBg,
            onPrimary = Color(0xFF04211B),
            onSurface = TextMain
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
                    onOpenGraph = { screen = Screen.Graph },
                    onOpenPool = { screen = Screen.Pool }
                )

                is Screen.Recommend -> RecommendScreen(
                    band = assessment.band,
                    pool = Repo.pool,
                    records = Repo.records,
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
                    heartRate = heartRate,
                    onFinish = { before ->
                        screen = Screen.Feedback(s.action, before)
                    },
                    onCancel = { screen = Screen.Home }
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

                is Screen.Pool -> PoolScreen(onBack = { screen = Screen.Home })
            }
        }
    }
}

// ---------------- 通用小组件 ----------------

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, height: Int = 8) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
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
    onOpenGraph: () -> Unit,
    onOpenPool: () -> Unit
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
                Text("回血实验室", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Text(
                    if (totalExperiments == 0) "还没做过实验 · 今天可以开始第一次"
                    else "已完成 $totalExperiments 次实验",
                    fontSize = 12.sp, color = TextDim
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (heartRate != null) Mint.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    heartRate?.let { "♥ $it bpm" } ?: "未连手环",
                    color = if (heartRate != null) Mint else TextDim,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionCard {
            Text("此刻精力余额", fontSize = 13.sp, color = TextDim)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${assessment.score}", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = accent)
                Spacer(Modifier.width(6.dp))
                Text("/ 100", fontSize = 14.sp, color = TextDim, modifier = Modifier.padding(bottom = 10.dp))
                Spacer(Modifier.weight(1f))
                Chip(assessment.band.label, accent)
            }
            Spacer(Modifier.height(12.dp))
            Bar(assessment.score / 100f, accent, 10)
            Spacer(Modifier.height(16.dp))
            Text(assessment.headline, fontSize = 16.sp, color = TextMain, fontWeight = FontWeight.Medium)

            Spacer(Modifier.height(10.dp))
            Text(
                if (showReasons) "收起判断依据 ▲" else "这个分数怎么来的？▼",
                fontSize = 13.sp, color = Mint,
                modifier = Modifier.clickable { showReasons = !showReasons }
            )
            if (showReasons) {
                Spacer(Modifier.height(8.dp))
                assessment.reasons.forEach {
                    Text("· $it", fontSize = 13.sp, color = TextDim, modifier = Modifier.padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "这里只做状态粗判，不做任何医学结论。",
                    fontSize = 11.sp, color = TextDim.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onNeedBreak,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF08221C))
        ) {
            Text("我现在有点撑不住", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))

        TextButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
            Text("说不清也没关系，跟它说一句 →", color = Mint, fontSize = 13.sp)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onOpenPool, modifier = Modifier.weight(1f)) {
                Text("我的回血池", color = TextDim, fontSize = 14.sp)
            }
            TextButton(onClick = onOpenGraph, modifier = Modifier.weight(1f)) {
                Text("回血图谱", color = TextDim, fontSize = 14.sp)
            }
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
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
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
    onPick: (ActionItem) -> Unit,
    onBack: () -> Unit
) {
    var batch by remember { mutableStateOf(0) }
    val recs = remember(band, records.size, pool.size, batch) {
        Recommender.recommend(band, pool, records)
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
                    .clickable { onPick(rec.action) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rec.action.emoji, fontSize = 26.sp)
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
            onClick = { batch++ },
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
    heartRate: Int?,
    onFinish: (Int?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var secondsLeft by remember { mutableStateOf(action.minutes * 60) }
    val total = action.minutes * 60
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
        Text(action.emoji, fontSize = 44.sp)
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

        Spacer(Modifier.height(40.dp))
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

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val labels = listOf("没用", "一般", "还行", "有用", "很管用")
            val emojis = listOf("😑", "🙂", "😌", "😊", "✨")
            labels.indices.forEach { i ->
                val selected = rating == i + 1
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Mint.copy(alpha = 0.18f) else CardBg)
                        .clickable { rating = i + 1 }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(emojis[i], fontSize = 22.sp)
                    Text(labels[i], fontSize = 11.sp, color = if (selected) Mint else TextDim)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionCard {
            Text("本次实验记录", fontSize = 13.sp, color = TextDim)
            Spacer(Modifier.height(8.dp))
            Text("${action.emoji}  ${action.title} · ${action.minutes} 分钟", fontSize = 15.sp, color = TextMain)
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
                        Text(st.action.emoji, fontSize = 20.sp)
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
private fun PoolScreen(onBack: () -> Unit) {
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
                Text(a.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, fontSize = 15.sp, color = TextMain)
                    Text("${a.category.label} · ${a.minutes} 分钟", fontSize = 11.sp, color = TextDim)
                }
                TextButton(onClick = { Repo.removeAction(a.id) }) { Text("删", color = TextDim) }
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