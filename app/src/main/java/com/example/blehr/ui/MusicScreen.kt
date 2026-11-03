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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.data.MusicApp
import com.example.blehr.data.MusicBinding
import com.example.blehr.logic.AmbientTrack
import com.example.blehr.logic.Emotion
import com.example.blehr.logic.MascotMood
import com.example.blehr.logic.MusicSuggestion
import com.example.blehr.logic.StressBand
import com.example.blehr.logic.StressResult
import com.example.blehr.player.AmbientPlayer
import kotlinx.coroutines.delay

/**
 * 音乐页。三件事摆在一起：
 *   1. 内置音景（真的能按一下就出声）；
 *   2. 小夜根据压力的推荐（她自己挑一段、说一句）；
 *   3. 绑定第三方 App（只负责"送你到门口"，播放由那个 App 决定）。
 */
@Composable
fun MusicScreen(
    stress: StressResult,
    hour: Int,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val player = remember { AmbientPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var suggestOn by remember { mutableStateOf(MusicBinding.suggestionsEnabled(ctx)) }
    var bound by remember { mutableStateOf(MusicBinding.bound(ctx)) }
    val installed = remember { MusicBinding.installed(ctx) }

    val plan = remember(stress.score, stress.emotion, hour) { MusicSuggestion.plan(stress, hour) }
    val showAdvice = stress.band != StressBand.CALM

    var elapsed by remember { mutableStateOf(0) }
    LaunchedEffect(player.playing) {
        elapsed = 0
        while (player.playing) {
            delay(1000L)
            elapsed++
        }
    }

    // 只有"开始放"才算被采纳：90 分钟冷却从这里起算，按暂停不算
    val play: (AmbientTrack) -> Unit = { track ->
        if (!(player.playing && player.current == track)) MusicBinding.markSuggested(ctx)
        player.toggle(track)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackRow("音乐", onBack)
        Spacer(Modifier.height(6.dp))
        Text("听点什么", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Look.Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "内置的三段音景是这台手机实时算出来的，不联网、没版权问题。",
            fontSize = 12.sp, color = Look.InkFaint
        )
        Spacer(Modifier.height(16.dp))

        // ---------- 小夜的建议 ----------
        if (showAdvice) {
            MCard(highlight = player.playing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GirlMascot(
                        mood = if (stress.band == StressBand.HIGH) MascotMood.WORRIED else MascotMood.IDLE,
                        size = 54.dp,
                        animated = false
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("小夜说", fontSize = 11.sp, color = Look.InkFaint)
                        Spacer(Modifier.height(2.dp))
                        Text(plan.line, fontSize = 14.sp, color = Look.Ink)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "「${plan.track.title}」· 建议 ${plan.track.minutes} 分钟",
                        fontSize = 12.sp, color = Look.InkSoft, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { play(plan.track) }) {
                        Text(
                            if (player.playing && player.current == plan.track) "暂停" else "放这段 →",
                            color = Look.Accent, fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---------- 三段音景 ----------
        SectionLabel("内置音景")
        AmbientTrack.entries.forEach { track ->
            val active = player.current == track && player.playing
            MCard(highlight = active, onClick = { play(track) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(
                                if (active) Look.Accent else Look.Card2,
                                RoundedCornerShape(12.dp)
                            ),
                        Alignment.Center
                    ) {
                        Glyph(
                            kind = if (active) GlyphKind.PAUSE else GlyphKind.PLAY,
                            size = 18.dp,
                            tint = if (active) Look.OnAccent else Look.InkSoft
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, fontSize = 15.sp, color = Look.Ink, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(track.hint, fontSize = 11.5.sp, color = Look.InkFaint)
                    }
                    if (active && elapsed > 0) {
                        Text(fmtTime(elapsed), fontSize = 11.sp, color = Look.Accent)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(GlyphKind.SPEAKER, size = 16.dp, tint = Look.InkFaint)
            Spacer(Modifier.width(8.dp))
            Slider(
                value = player.volume,
                onValueChange = { player.volume = it },
                modifier = Modifier.weight(1f)
            )
            Text("${(player.volume * 100).toInt()}%", fontSize = 11.sp, color = Look.InkFaint)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "离开这一页音乐就停 —— 我们没申请后台播放，不想做成一个偷偷跑的东西。",
            fontSize = 11.sp, color = Look.InkFaint
        )

        Spacer(Modifier.height(18.dp))

        // ---------- 第三方 ----------
        SectionLabel("绑定我的音乐 App")
        MCard {
            Text(
                "你手机里装了这几个：",
                fontSize = 12.sp, color = Look.InkSoft
            )
            Spacer(Modifier.height(8.dp))
            if (installed.isEmpty()) {
                Text(
                    "没找到 QQ音乐 / 网易云 / 酷狗 / 酷我。装一个之后回来这里就能绑定。",
                    fontSize = 12.5.sp, color = Look.InkFaint
                )
            } else {
                installed.forEach { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                bound = app
                                MusicBinding.setBound(ctx, app)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .border(
                                    1.5.dp,
                                    if (bound?.pkg == app.pkg) Look.Accent else Look.Hair,
                                    RoundedCornerShape(50)
                                ),
                            Alignment.Center
                        ) {
                            if (bound?.pkg == app.pkg) {
                                Box(
                                    Modifier
                                        .size(9.dp)
                                        .background(Look.Accent, RoundedCornerShape(50))
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(app.label, fontSize = 14.sp, color = Look.Ink)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            val target = bound ?: installed.first()
                            bound = target
                            MusicBinding.setBound(ctx, target)
                            MusicBinding.copyKeyword(ctx)
                            MusicBinding.openApp(ctx, target)
                        }
                    ) { Text("打开并搜「${MusicBinding.KEYWORD}」→", color = Look.Accent, fontSize = 13.sp) }
                }
                Text(
                    "第三方 App 没有开放「播放指定歌曲」的公共接口，我们只能把你送到它的搜索页，" +
                        "关键词已经复制到剪贴板 —— 剩下点一下是你的选择。",
                    fontSize = 11.sp, color = Look.InkFaint
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- 开关 ----------
        MCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("让小夜主动推音乐", fontSize = 14.sp, color = Look.Ink)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "只在真的偏紧时推，两次之间至少隔 90 分钟；半夜她只在你还醒着时才开口。",
                        fontSize = 11.5.sp, color = Look.InkFaint
                    )
                }
                Switch(
                    checked = suggestOn,
                    onCheckedChange = {
                        suggestOn = it
                        MusicBinding.setSuggestionsEnabled(ctx, it)
                    }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "压力是这么估的：心率变异率（RMSSD）为主，加上心率比平时高多少、连续用了多久、几点。\n" +
                "它不是医学测量，情绪也只是推断 —— 依据都摊在首页那一行下面，能点开看。",
            fontSize = 11.sp, color = Look.InkFaint
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = Look.InkFaint,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun MCard(
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val base = Modifier
        .fillMaxWidth()
        .background(if (highlight) Look.Card2 else Color.White, RoundedCornerShape(16.dp))
        .border(1.dp, if (highlight) Look.Accent.copy(alpha = 0.45f) else Look.Hair, RoundedCornerShape(16.dp))
    Column(
        (if (onClick != null) base.clickable { onClick() } else base).padding(14.dp)
    ) { content() }
}

private fun fmtTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}