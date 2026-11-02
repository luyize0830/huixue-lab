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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.logic.PersonaPreset

/**
 * 「小夜的性格」——把提示词交给用户。
 *
 * 设计上的三个考虑：
 * 1. 预设 + 自由编辑两条路都留着。多数人不想写提示词，但想让她"别这么端着"时，
 *    必须有一条不用懂提示词的捷径；而愿意写的人，应该能直接写。
 * 2. 改完立刻生效：保存只写本地，下次发消息时现读，不需要重启。
 * 3. 老实说明生效条件——没接模型时，聊天走的是本地规则，这段文字不起作用。
 */
@Composable
fun PersonaScreen(
    initialPresetId: String,
    initialCustom: String,
    onSave: (presetId: String, custom: String) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    var presetId by remember { mutableStateOf(initialPresetId) }
    var custom by remember { mutableStateOf(initialCustom) }
    var savedTip by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Look.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BackRow("小夜的性格", onBack)
        Spacer(Modifier.height(4.dp))
        Text(
            "她怎么说话，由你定。",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Look.Ink
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "下面是写给她的一句话说明书，会随每条消息一起发给你的模型。我们看不到它，也不经过我们的服务器。",
            fontSize = 12.sp,
            color = Look.InkSoft,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(16.dp))

        PersonaPreset.all.forEach { p ->
            PresetRow(
                title = p.title,
                hint = p.hint,
                sample = p.sample,
                selected = presetId == p.id,
                onClick = {
                    presetId = p.id
                    savedTip = null
                }
            )
            Spacer(Modifier.height(10.dp))
        }

        if (PersonaPreset.isCustom(presetId)) {
            Spacer(Modifier.height(10.dp))
            Text(
                "你自己写的。想怎么写就怎么写，比如「说话像东北大姨」。",
                fontSize = 12.sp,
                color = Look.InkSoft
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = custom,
                onValueChange = {
                    custom = it
                    savedTip = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                placeholder = {
                    Text(
                        "例：一句话就够。我说累，你就说「嗯，累啊」。",
                        fontSize = 13.sp,
                        color = Look.InkFaint
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Look.Accent,
                    unfocusedBorderColor = Look.Hair,
                    focusedContainerColor = Look.Card,
                    unfocusedContainerColor = Look.Card,
                    focusedTextColor = Look.Ink,
                    unfocusedTextColor = Look.Ink
                )
            )
            Spacer(Modifier.height(14.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Look.Accent)
                .clickable {
                    onSave(presetId, custom)
                    savedTip = "存好了，回去跟她说一句试试。"
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("保存", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Look.OnAccent)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Look.Hair, RoundedCornerShape(14.dp))
                .clickable {
                    onReset()
                    presetId = PersonaPreset.DEFAULT_ID
                    custom = ""
                    savedTip = "已经回到默认的小夜。"
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("恢复默认", fontSize = 14.sp, color = Look.InkSoft)
        }

        savedTip?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, fontSize = 12.sp, color = Look.Olive)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "还有一件事：没接入你自己的模型时，她用的是本地规则，这段文字不生效。接入之后才会按你写的说。",
            fontSize = 11.sp,
            color = Look.InkFaint,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PresetRow(
    title: String,
    hint: String,
    sample: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Look.Card2 else Look.Card)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Look.Accent else Look.Hair,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Look.Ink)
                Spacer(Modifier.height(3.dp))
                Text(hint, fontSize = 11.5.sp, color = Look.InkFaint)
            }
            if (selected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Look.Accent)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("已选", fontSize = 11.sp, color = Look.Accent)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(sample, fontSize = 12.5.sp, color = Look.InkSoft, lineHeight = 18.sp)
    }
}