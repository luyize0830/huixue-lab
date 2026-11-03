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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.data.Profile
import com.example.blehr.logic.MascotMood
import com.example.blehr.logic.Persona

/**
 * 登录 / 认脸。
 *
 * 这不是账号系统：没有服务器、没有密码、没有验证码。
 * 存在的理由只有一个——让小夜知道怎么喊你，并且在你换手机之前记得你。
 * 所以界面上必须把"只存在这台手机里"写在显眼处，而不是藏在隐私条款里。
 */
@Composable
fun LoginScreen(
    initial: Profile,
    editMode: Boolean,
    onDone: (nickname: String, school: String, major: String) -> Unit,
    onSkip: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var nickname by remember { mutableStateOf(initial.nickname) }
    var school by remember { mutableStateOf(initial.school) }
    var major by remember { mutableStateOf(initial.major) }

    val name = nickname.trim()
    val canGo = name.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .background(Look.Paper)
    ) {
        if (onBack != null) {
            BackRow(if (editMode) "编辑资料" else "登录", onBack)
        } else {
            Spacer(Modifier.height(36.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!editMode) {
                GirlMascot(mood = MascotMood.IDLE, size = 156.dp)
                Spacer(Modifier.height(6.dp))
            }

            Text(
                if (editMode) "改一下怎么称呼你" else "先认识一下",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Look.Ink
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (editMode) "改完之后她下一次开口就会用新名字。"
                else "她叫${Persona.NAME}。告诉她你想被怎么称呼，剩下的她自己会看。",
                fontSize = 13.sp,
                color = Look.InkSoft,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(22.dp))

            // 一边输一边就有反馈：这是"认脸"最便宜的做法
            if (name.isNotEmpty() && !editMode) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Look.SeaBelly.copy(alpha = 0.30f))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GirlMascot(mood = MascotMood.HAPPY, size = 26.dp, animated = false)
                    Spacer(Modifier.size(9.dp))
                    Text(
                        "「你好，$name。」",
                        fontSize = 13.sp,
                        color = Look.SeaDeep,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            LoginField(
                label = "怎么称呼你",
                value = nickname,
                onValueChange = { if (it.length <= 12) nickname = it },
                placeholder = "昵称也行，随便一个代号也行",
                required = true,
                limit = 12
            )

            Spacer(Modifier.height(14.dp))

            LoginField(
                label = "学校（可选）",
                value = school,
                onValueChange = { if (it.length <= 20) school = it },
                placeholder = "以后要做多人对比会用到",
                limit = 20
            )

            Spacer(Modifier.height(14.dp))

            LoginField(
                label = "专业（可选）",
                value = major,
                onValueChange = { if (it.length <= 20) major = it },
                placeholder = "计算机 / 智能科学 / 什么都行",
                limit = 20
            )

            Spacer(Modifier.height(26.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canGo) Look.Accent else Look.Track)
                    .clickable(enabled = canGo) { onDone(name, school.trim(), major.trim()) }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (editMode) "保存" else "就这样开始",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (canGo) Look.OnAccent else Look.InkFaint
                )
            }

            if (!editMode) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "先不填，随便逛逛 →",
                    fontSize = 13.sp,
                    color = Look.InkFaint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onSkip)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(22.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Look.Hair, RoundedCornerShape(14.dp))
                    .background(Look.Card)
                    .padding(14.dp)
            ) {
                Text("这不是账号系统", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Look.Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "没有服务器、没有密码、不需要手机号。名字只存在这台手机的 App 私有目录里，"
                        + "卸载就没了，也不会上传给任何人——包括她。"

                        + "",
                    fontSize = 11.sp,
                    color = Look.InkFaint,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "为什么还要填：她得知道怎么喊你，而且换手机之前你能导出记录。",
                    fontSize = 11.sp,
                    color = Look.InkFaint,
                    lineHeight = 17.sp
                )
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun LoginField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    required: Boolean = false,
    limit: Int
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Look.Ink)
            if (required) {
                Spacer(Modifier.size(4.dp))
                Text("必填", fontSize = 10.sp, color = Look.Clay)
            }
            Spacer(Modifier.weight(1f))
            if (value.isNotEmpty()) {
                Text("${value.length}/$limit", fontSize = 10.sp, color = Look.InkFaint)
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, fontSize = 13.sp, color = Look.InkFaint) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = Look.Ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
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
}
