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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.data.Profile
import com.example.blehr.data.WearableSnapshot

/**
 * 「我的」页：账号 + 设备与数据授权 + 陪练 + 记录。
 *
 * 这里的取舍：所有需要用户"去别的地方点一下"的事，都收在这一页里，
 * 并且每一行都写清"现在是什么状态、点一下会发生什么"。
 * 状态不明的按钮是上一版最大的坑（点了没反应，用户不知道是成功还是失败）。
 */
@Composable
fun MineScreen(
    profile: Profile,
    onSaveProfile: (String, String, String) -> Unit,
    onSignOut: () -> Unit,
    heartRate: Int?,
    connectedName: String?,
    wearable: WearableSnapshot,
    usageAuthorized: Boolean,
    activityAvailable: Boolean,
    nudgeEnabled: Boolean,
    onToggleNudge: (Boolean) -> Unit,
    recordCount: Int,
    onOpenScan: () -> Unit,
    onRequestHealth: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onRequestActivity: () -> Unit,
    onOpenJoin: () -> Unit,
    onExport: () -> Unit,
    onClearRecords: () -> Unit,
    versionText: String
) {
    var editing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("我的", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Look.Ink)
        Spacer(Modifier.height(4.dp))
        Text("账号、设备与授权都收在这里", fontSize = 12.sp, color = Look.InkFaint)
        Spacer(Modifier.height(16.dp))

        // ---- 账号卡 ----
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Look.Card)
                .border(1.dp, Look.Hair, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Look.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (profile.signedIn) {
                    Text(
                        profile.initial,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Look.Accent
                    )
                } else {
                    Glyph(GlyphKind.PEOPLE, size = 26.dp, tint = Look.Accent)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (profile.signedIn) profile.nickname else "还没登录",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Look.Ink
                )
                Spacer(Modifier.height(3.dp))
                Text(profile.subtitle, fontSize = 12.sp, color = Look.InkSoft)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Look.Accent.copy(alpha = 0.10f))
                    .clickable { editing = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    if (profile.signedIn) "编辑" else "登录",
                    fontSize = 13.sp, color = Look.Accent, fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "本地账号：昵称和学校只存在这台手机上，我们没有服务器，也不需要你的密码。" +
                "以后接入 OPPO / 学校账号时，这一层会换成真正的登录。",
            fontSize = 11.sp, color = Look.InkFaint
        )

        // ---- 设备与数据 ----
        Spacer(Modifier.height(20.dp))
        SectionTitle("设备与数据")

        EntryCard(
            title = if (heartRate != null) "手环已连接" else "手环（可选）",
            subtitle = when {
                heartRate != null -> "正在读 $heartRate bpm" +
                    (connectedName?.let { " · $it" } ?: "")
                else -> "没连也能用，只是少了客观心率"
            },
            chipText = if (heartRate != null) "已连接" else "去连接",
            chipColor = if (heartRate != null) Look.Olive else Look.Accent,
            onClick = onOpenScan
        )

        Spacer(Modifier.height(8.dp))
        val hcChip: String
        val hcColor: androidx.compose.ui.graphics.Color
        val hcSub: String
        when {
            !wearable.available -> {
                hcChip = "去安装"; hcColor = Look.Wheat
                hcSub = "这台手机没有 Health Connect（健康数据平台），点一下去装/去开"
            }
            !wearable.authorized -> {
                hcChip = "去授权"; hcColor = Look.Wheat
                hcSub = "一次授权就能读步数与睡眠，不用一个个点"
            }
            else -> {
                hcChip = "已接入"; hcColor = Look.Olive
                hcSub = listOfNotNull(
                    wearable.sleepMinutesLastNight?.let { "昨夜 ${it / 60}h${it % 60}m" },
                    wearable.stepsToday?.let { "今天 ${it} 步" }
                ).joinToString(" · ").ifBlank { wearable.message ?: "已授权，暂无可读数据" }
            }
        }
        EntryCard(
            title = "手环历史（Health Connect）",
            subtitle = hcSub,
            chipText = hcChip,
            chipColor = hcColor,
            onClick = onRequestHealth
        )

        Spacer(Modifier.height(8.dp))
        EntryCard(
            title = "手机使用情况",
            subtitle = if (usageAuthorized) "已授权，正在用真实数据判断连续专注"
            else "授权后不再用演示数据：连续使用多久、近一小时前台多久",
            chipText = if (usageAuthorized) "已授权" else "去设置",
            chipColor = if (usageAuthorized) Look.Olive else Look.Wheat,
            onClick = onOpenUsageSettings
        )

        Spacer(Modifier.height(8.dp))
        EntryCard(
            title = "身体活动（计步）",
            subtitle = if (activityAvailable) "已授权，正在判断「多久没起身走动」"
            else "授权后能知道你今天走了几步、坐了多久",
            chipText = if (activityAvailable) "已授权" else "去授权",
            chipColor = if (activityAvailable) Look.Olive else Look.Wheat,
            onClick = onRequestActivity
        )

        // ---- 陪练 ----
        Spacer(Modifier.height(20.dp))
        SectionTitle("和朋友一起")
        EntryCard(
            title = "陪练码",
            subtitle = "不联网：一串 8 位码，两边各自倒计时，2 小时内有效",
            chipText = "加入",
            chipColor = Look.Accent,
            onClick = onOpenJoin
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "玩法：倒计时页点「拉个人一起」生成码，发给朋友；朋友在这里输入就能和你同一秒开始。",
            fontSize = 11.sp, color = Look.InkFaint
        )

        // ---- 提醒 ----
        Spacer(Modifier.height(20.dp))
        SectionTitle("提醒")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Look.Card)
                .border(1.dp, Look.Hair, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("状态下滑时提醒我", fontSize = 14.sp, color = Look.Ink)
                Text(
                    "只在连续专注太久、久坐超 1 小时或分数偏低时才发，两次至少隔 50 分钟",
                    fontSize = 11.sp, color = Look.InkFaint
                )
            }
            Switch(
                checked = nudgeEnabled,
                onCheckedChange = onToggleNudge,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Look.OnAccent,
                    checkedTrackColor = Look.Accent
                )
            )
        }

        // ---- 记录 ----
        Spacer(Modifier.height(20.dp))
        SectionTitle("记录")
        EntryCard(
            title = "一共 $recordCount 次实验",
            subtitle = "导出一份 CSV，可以拿去写报告或做自己的复盘",
            chipText = "导出",
            chipColor = Look.Accent,
            onClick = onExport
        )
        Spacer(Modifier.height(8.dp))
        EntryCard(
            title = "清空所有记录",
            subtitle = "回血池里你自己加的行动会保留",
            chipText = "清空",
            chipColor = Look.Clay,
            onClick = { confirmClear = true }
        )

        // ---- 关于 ----
        Spacer(Modifier.height(20.dp))
        SectionTitle("关于")
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Look.Card2)
                .padding(16.dp)
        ) {
            Text("回血实验室 $versionText", fontSize = 13.sp, color = Look.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "所有数据都在本机：记录存在应用私有目录，导出的 CSV 由你自己决定发给谁。" +
                    "心率只在连接手环时读取，断开即停。",
                fontSize = 11.sp, color = Look.InkSoft
            )
            if (profile.signedIn) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "退出登录（只清昵称，记录不动）",
                    fontSize = 12.sp,
                    color = Look.Clay,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSignOut() }
                        .padding(vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (editing) {
        ProfileDialog(
            profile = profile,
            onDismiss = { editing = false },
            onSave = { n, s, m ->
                onSaveProfile(n, s, m)
                editing = false
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有记录？", color = Look.Ink) },
            text = { Text("清空后回血图谱会变空，这个操作不能撤销。", color = Look.InkSoft) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearRecords() }) {
                    Text("清空", color = Look.Clay)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("算了", color = Look.InkSoft)
                }
            },
            containerColor = Look.Card
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    SmallCaps(text)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun EntryCard(
    title: String,
    subtitle: String,
    chipText: String,
    chipColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Look.Card)
            .border(1.dp, Look.Hair, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Look.Ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 11.sp, color = Look.InkFaint)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(chipColor.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(chipText, fontSize = 12.sp, color = chipColor, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ProfileDialog(
    profile: Profile,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var nickname by remember { mutableStateOf(profile.nickname) }
    var school by remember { mutableStateOf(profile.school) }
    var major by remember { mutableStateOf(profile.major) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile.signedIn) "编辑资料" else "登录（本地）", color = Look.Ink) },
        text = {
            Column {
                Text(
                    "不用密码，也不会传出去。写个称呼，记录就有归属了。",
                    fontSize = 12.sp, color = Look.InkSoft
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Look.Accent,
                        unfocusedBorderColor = Look.Hair,
                        focusedLabelColor = Look.Accent,
                        cursorColor = Look.Accent
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = school,
                    onValueChange = { school = it },
                    label = { Text("学校（可空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Look.Accent,
                        unfocusedBorderColor = Look.Hair,
                        focusedLabelColor = Look.Accent,
                        cursorColor = Look.Accent
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = major,
                    onValueChange = { major = it },
                    label = { Text("专业（可空）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Look.Accent,
                        unfocusedBorderColor = Look.Hair,
                        focusedLabelColor = Look.Accent,
                        cursorColor = Look.Accent
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (nickname.isNotBlank()) onSave(nickname, school, major) },
                enabled = nickname.isNotBlank()
            ) { Text(if (profile.signedIn) "保存" else "进入", color = Look.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Look.InkSoft) }
        },
        containerColor = Look.Card
    )
}