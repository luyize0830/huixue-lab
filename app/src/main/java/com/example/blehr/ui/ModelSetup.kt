package com.example.blehr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.data.LlmClient
import com.example.blehr.data.LlmConfig
import com.example.blehr.logic.Persona
import kotlinx.coroutines.launch

/**
 * 接入自己的模型。
 *
 * 为什么把它做成一个独立页面而不是埋在设置里：
 * 用户第一次打开 App 时，首页那只角色是灰的、说不了话，
 * 缺的就是这一步。找不到入口 = 这个功能不存在。
 */
@Composable
fun ModelSetupScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var base by remember { mutableStateOf(LlmConfig.baseUrl(ctx)) }
    var model by remember { mutableStateOf(LlmConfig.model(ctx)) }
    var key by remember { mutableStateOf(LlmConfig.apiKey(ctx)) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<LlmClient.TestResult?>(null) }

    val canSave = key.trim().isNotEmpty() && base.trim().isNotEmpty() && model.trim().isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .background(Look.Paper)
    ) {
        BackRow("接入我的模型", onBack)

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "你有自己的 API，就用你自己的。",
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = Look.Ink
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "填完这一页，${Persona.NAME}就上线了：能跟你说话、记得上次聊到哪、状态不好的时候先开口。"
                    + "不填也行，App 会一直用本地规则，只是她不会说话。",
                fontSize = 13.sp,
                color = Look.InkSoft,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(18.dp))

            FieldLabel("服务地址")
            Spacer(Modifier.height(6.dp))
            LookField(
                value = base,
                onValueChange = { base = it; result = null },
                placeholder = "https://api.deepseek.com/v1",
                singleLine = true,
                keyboard = KeyboardType.Uri
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "填到 /v1 就行，后面那段我们补。也可以粘完整的 …/chat/completions。",
                fontSize = 11.sp,
                color = Look.InkFaint,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(16.dp))

            FieldLabel("模型名")
            Spacer(Modifier.height(6.dp))
            LookField(
                value = model,
                onValueChange = { model = it; result = null },
                placeholder = LlmConfig.DEFAULT_MODEL,
                singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "DeepSeek 填 deepseek-chat；换成别家，填它文档里那个名字。",
                fontSize = 11.sp,
                color = Look.InkFaint,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("API Key")
                Spacer(Modifier.weight(1f))
                Text(
                    if (showKey) "隐藏" else "显示",
                    fontSize = 11.sp,
                    color = Look.SeaDeep,
                    modifier = Modifier.clickable { showKey = !showKey }
                )
            }
            Spacer(Modifier.height(6.dp))
            LookField(
                value = key,
                onValueChange = { key = it; result = null },
                placeholder = "sk-…",
                singleLine = true,
                keyboard = KeyboardType.Password,
                password = !showKey
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "只保存在这台手机的 App 私有目录里，我们没有服务器可以替你存。"
                    + "要撤掉，点下面的「清除」。",
                fontSize = 11.sp,
                color = Look.InkFaint,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(18.dp))

            // 测一下：失败也要说清是哪种失败
            Row(verticalAlignment = Alignment.CenterVertically) {
                SetupButton(
                    text = if (testing) "正在试…" else "测一下能不能连上",
                    enabled = !testing,
                    filled = false,
                    onClick = {
                        testing = true
                        result = null
                        val testedInput = Triple(base, model, key)
                        scope.launch {
                            try {
                                val tested = LlmClient.test(testedInput.first, testedInput.second, testedInput.third)
                                if (testedInput == Triple(base, model, key)) result = tested
                            } finally {
                                testing = false
                            }
                        }
                    }
                )
            }

            result?.let { r ->
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (r.ok) Look.Olive.copy(alpha = 0.10f) else Look.Clay.copy(alpha = 0.08f)
                        )
                        .border(
                            1.dp,
                            if (r.ok) Look.Olive.copy(alpha = 0.35f) else Look.Clay.copy(alpha = 0.30f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        if (r.ok) "连上了" else "没连上",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (r.ok) Look.Olive else Look.Clay
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(r.message, fontSize = 12.sp, color = Look.InkSoft, lineHeight = 18.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            SetupButton(
                text = "保存并启用",
                enabled = canSave,
                filled = true,
                onClick = {
                    LlmConfig.setBaseUrl(ctx, base)
                    LlmConfig.setApiKey(ctx, key)
                    LlmConfig.setModelName(ctx, model)
                    LlmConfig.setEnabled(ctx, true)
                    onSaved()
                }
            )

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Look.Hair, RoundedCornerShape(14.dp))
                    .clickable {
                        LlmConfig.clear(ctx)
                        onSaved()
                    }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("清除，并回到本地规则", fontSize = 13.sp, color = Look.InkSoft)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "支持任何 OpenAI 兼容的接口（DeepSeek、通义、Kimi、ollama 本地都行）。"
                    + "发出去的只有你那句话、最近六轮对话和一张行动清单 —— 不含心率，也不含实验记录。",
                fontSize = 11.sp,
                color = Look.InkFaint,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Look.Ink)
}

@Composable
private fun LookField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, fontSize = 13.sp, color = Look.InkFaint) },
        singleLine = singleLine,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Look.Ink),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Look.Card,
            unfocusedContainerColor = Look.Card,
            focusedIndicatorColor = Look.Accent,
            unfocusedIndicatorColor = Look.Hair,
            cursorColor = Look.Accent
        )
    )
}

@Composable
private fun SetupButton(text: String, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    val bg = when {
        !filled -> Look.Card
        enabled -> Look.Accent
        else -> Look.Track
    }
    val fg = when {
        !filled -> if (enabled) Look.InkSoft else Look.InkFaint
        enabled -> Look.OnAccent
        else -> Look.InkFaint
    }
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, if (filled) bg else Look.Hair, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, color = fg, fontWeight = if (filled) FontWeight.Medium else FontWeight.Normal)
    }
}
