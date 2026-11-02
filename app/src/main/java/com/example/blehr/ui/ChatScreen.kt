package com.example.blehr.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.data.LlmClient
import com.example.blehr.data.LlmConfig
import com.example.blehr.logic.ChatAdvisor
import com.example.blehr.logic.IntentParser
import com.example.blehr.logic.LlmReplyParser
import com.example.blehr.model.ActionItem
import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ExperimentRecord
import kotlinx.coroutines.launch

private val CBg = Look.Paper
private val CCard = Look.Card
private val CBubble = Look.Card2
private val CMint = Look.Olive
private val CText = Look.Ink
private val CDim = Look.InkSoft

private data class ChatTurn(
    val fromUser: Boolean,
    val text: String,
    val actions: List<ActionItem> = emptyList()
)

/**
 * 对话式入口。
 *
 * 它是"AI 走出对话框"的反向用法：不是让用户去跟 AI 聊天，而是让用户用最省力的方式
 * 说清"我现在什么情况"，系统把它翻成结构化条件再落到具体行动上。
 */
@Composable
fun ChatScreen(
    band: EnergyBand,
    pool: List<ActionItem>,
    records: List<ExperimentRecord>,
    hour: Int,
    onPick: (ActionItem) -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var turns by remember {
        mutableStateOf(
            listOf(
                ChatTurn(
                    fromUser = false,
                    text = "说一句你现在的情况就行，比如「在图书馆，还有一小时，有点撑不住」。\n" +
                        "我会按你的时间、地点和状态挑几个五分钟内能做完的事。"
                )
            )
        )
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var voiceError by remember { mutableStateOf<String?>(null) }
    var thinking by remember { mutableStateOf(false) }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || thinking) return

        // 先把用户这句挂上去，后面不管走模型还是本地规则，界面都已经有反馈
        turns = turns + ChatTurn(true, t)
        input = ""

        val intent = IntentParser.parse(t, hour)
        val local = ChatAdvisor.reply(intent, band, pool, records)

        if (!LlmConfig.isReady(context)) {
            turns = turns + ChatTurn(false, local.text, local.recommendations.map { it.action })
            scope.launch { listState.animateScrollToItem(turns.size) }
            return
        }

        thinking = true
        scope.launch {
            val suggestion = LlmClient.ask(context, t, pool, hour, band)
            val picked = suggestion?.let { LlmReplyParser.pick(pool, it.actionIds) }.orEmpty()
            val turn = if (suggestion != null && picked.isNotEmpty()) {
                ChatTurn(false, suggestion.reply, picked)
            } else {
                // 模型不可用不是错误，只是降级：把"这是本地规则答的"直接告诉用户
                ChatTurn(
                    false,
                    local.text + "\n\n（刚才没连上模型，这是本地规则给的）",
                    local.recommendations.map { it.action }
                )
            }
            turns = turns + turn
            thinking = false
            listState.animateScrollToItem(turns.size)
        }
    }

    // 语音走系统识别（RecognizerIntent），App 自己不录音、不要录音权限：
    // 累到不想打字的时候，说一句比点开键盘省力得多。
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val said = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == Activity.RESULT_OK && !said.isNullOrBlank()) {
            voiceError = null
            input = said
            send(said)
        } else {
            voiceError = "没听清，再说一次？也可以直接打字。"
        }
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说一句你现在的情况")
        }
        val resolved = runCatching { intent.resolveActivity(context.packageManager) }.getOrNull()
        if (resolved == null) {
            voiceError = "这台手机没装语音识别服务，先打字吧。"
        } else {
            runCatching { voiceLauncher.launch(intent) }
                .onFailure { voiceError = "语音识别没能打开：${it.message ?: "未知原因"}" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CBg)
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← 返回", color = CDim) }
            Spacer(Modifier.weight(1f))
            Text("说一句话", color = CText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(64.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(turns) { _, turn ->
                if (turn.fromUser) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            turn.text,
                            color = Look.OnAccent,
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .background(CMint, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            turn.text,
                            color = CText,
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .background(CBubble, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        if (turn.actions.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            turn.actions.forEach { a ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .border(1.dp, Look.Hair, RoundedCornerShape(12.dp))
                                        .clickable { onPick(a) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = CCard)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CategoryTag(a.category, size = 28.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(a.title, color = CText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            Text("${a.minutes} 分钟 · ${a.cue}", color = CDim, fontSize = 11.sp, lineHeight = 15.sp)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text("开始 →", color = CMint, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                if (thinking) {
                    Text(
                        "正在想…",
                        color = CDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // 快捷句：答辩现场手不太方便打长句，点一下就能演示
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("有点撑不住", "还剩十分钟", "在图书馆").forEach { quick ->
                Text(
                    quick,
                    color = CDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(CBubble, RoundedCornerShape(20.dp))
                        .clickable { send(quick) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        voiceError?.let {
            Text(
                it,
                color = CDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(4.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("说一句现在的状态…", color = CDim, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CMint,
                    unfocusedBorderColor = CBubble,
                    focusedTextColor = CText,
                    unfocusedTextColor = CText,
                    cursorColor = CMint
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { send(input) },
                colors = ButtonDefaults.buttonColors(containerColor = CMint),
                shape = RoundedCornerShape(14.dp)
            ) { Text("发", color = Look.OnAccent, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(6.dp))
            TextButton(
                onClick = { startVoice() },
                shape = RoundedCornerShape(14.dp)
            ) { Glyph(GlyphKind.MIC, size = 20.dp, tint = Look.InkSoft) }
        }
    }
}