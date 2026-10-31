package com.example.blehr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.logic.Pairing
import com.example.blehr.model.ActionItem

private val JBg = Look.Paper
private val JCard = Look.Card
private val JMint = Look.Olive
private val JText = Look.Ink
private val JDim = Look.InkSoft
private val JRose = Look.Clay

/**
 * 用陪练码加入一场别人已经开始的实验。
 *
 * 这里没有任何联网：码本身就携带了"哪个行动、几分钟、几点开始"，
 * 两台设备各自本地算剩余时间。跨设备同步靠的是时间，不是服务器——
 * 好处是断网也能用、隐私不出手机，代价是两台设备的时钟差多少就偏多少。
 */
@Composable
fun JoinScreen(
    pool: List<ActionItem>,
    onJoined: (ActionItem, Long) -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(JBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("← 返回", color = JDim) }
        }
        Spacer(Modifier.height(8.dp))
        Text("用陪练码加入", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = JText)
        Text(
            "朋友在计时页点了「拉个人一起」，会得到一串 8 位码。输进来，两边就是同一场实验。",
            fontSize = 12.sp, color = JDim
        )

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("陪练码", color = JDim) },
            placeholder = { Text("ABCD-1234", color = JDim) },
            shape = RoundedCornerShape(14.dp)
        )

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, fontSize = 12.sp, color = JRose)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val code = Pairing.decode(input, pool, System.currentTimeMillis())
                if (code == null) {
                    error = "这个码解析不出来：可能是打错了、超过两小时，或者对应的行动已经不在你的回血池里。"
                } else {
                    val action = pool.firstOrNull { it.id == code.actionId }
                    if (action == null) {
                        error = "码是对应「${code.actionId}」的，但你的回血池里没有这个行动。"
                    } else if (Pairing.expired(code.minutes, code.startAtMs, System.currentTimeMillis())) {
                        error = "这一场已经结束了（${code.minutes} 分钟前开始的）。可以让他再开一场。"
                    } else {
                        onJoined(action, code.startAtMs)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("加入这一场", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.border(1.dp, Look.Hair, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = JCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("没收到码？", fontSize = 13.sp, color = JText, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "让朋友在他的计时页点「一个人没劲？拉个人一起 →」，生成后直接「发给朋友」。\n" +
                        "如果两个人只是各做各的，不加入也行——这里不强制社交，只是给「一个人撑不住」的时候留条路。",
                    fontSize = 12.sp, color = JDim, lineHeight = 18.sp
                )
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}