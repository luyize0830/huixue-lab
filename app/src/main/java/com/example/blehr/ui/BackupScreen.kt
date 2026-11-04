package com.example.blehr.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.blehr.data.BackupManager
import com.example.blehr.data.Repo
import com.example.blehr.data.ProfileRepo
import com.example.blehr.logic.BackupCodec
import com.example.blehr.logic.BackupData
import com.example.blehr.logic.LabCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(onBack: () -> Unit, onRestored: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<BackupData?>(null) }
    var undoConfirm by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var undoAvailable by remember { mutableStateOf(BackupManager.hasUndo(ctx)) }
    BackHandler { if (!busy) onBack() }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = BackupCodec.encode(BackupManager.capture(ctx))
                    BackupCodec.decode(text)
                    checkNotNull(ctx.contentResolver.openOutputStream(uri, "wt")) { "无法写入所选文件" }
                        .bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
            }.onSuccess { message = "备份已保存到你选择的位置。" }
                .onFailure { message = "备份失败：${it.message ?: "无法写入文件"}" }
            busy = false
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = ByteArrayOutputStream()
                    checkNotNull(ctx.contentResolver.openInputStream(uri)) { "无法读取文件" }.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(bytes.size() + count <= LabCodec.MAX_BYTES) { "备份不能超过 8 MB" }
                            bytes.write(buffer, 0, count)
                        }
                    }
                    BackupCodec.decode(bytes.toString("UTF-8"))
                }
            }.onSuccess { preview = it; message = "已检查文件，尚未修改本机数据。" }
                .onFailure { message = "无法导入：文件损坏、版本不匹配或内容不完整。本机数据未改动。" }
            busy = false
        }
    }
    fun restore(data: BackupData?, undo: Boolean = false) {
        scope.launch {
            busy = true
            runCatching {
                withContext(NonCancellable) {
                    val restored = withContext(Dispatchers.IO) {
                        if (undo) BackupManager.undo(ctx) else BackupManager.restore(ctx, checkNotNull(data))
                    }
                    Repo.acceptRestored(restored)
                    ProfileRepo.init(ctx)
                }
            }.onSuccess {
                    preview = null; undoConfirm = false; undoAvailable = true
                    message = if (undo) "已恢复到上一次导入前。" else "导入完成。再次导入同一份文件不会叠加记录。"
                    onRestored()
                }.onFailure { message = "恢复未完成：${it.message ?: "请重试"}" }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        BackRow("备份与恢复", { if (!busy) onBack() })
        Text("备份行动、实验、收藏、归档、进行中的行动、聊天、个人档案和偏好。", color = Look.Ink)
        Spacer(Modifier.height(8.dp))
        Text("文件含个人内容，请妥善保存。模型密钥、设备连接和系统权限不会导出；导入后保留本机的模型设置。", color = Look.InkSoft)
        Spacer(Modifier.height(16.dp))
        Button(enabled = !busy, onClick = {
            val date = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
            export.launch("huixue-backup-$date.json")
        }, modifier = Modifier.fillMaxWidth()) { Text("导出完整备份") }
        OutlinedButton(enabled = !busy && Repo.session == null, onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth()) { Text("选择备份文件") }
        if (Repo.session != null) Text("请先完成或取消进行中的行动，再导入或撤销导入。", color = Look.InkSoft)
        if (undoAvailable) TextButton(enabled = !busy && Repo.session == null, onClick = { undoConfirm = true }) {
            Text("恢复到上一次导入前")
        }
        if (undoAvailable) TextButton(enabled = !busy, onClick = { deleteConfirm = true }) { Text("删除本机恢复副本") }
        if (busy) { CircularProgressIndicator(); Text("正在处理，请稍候…") }
        if (message.isNotBlank()) Text(message, modifier = Modifier.padding(vertical = 12.dp), color = Look.Ink)
    }
    preview?.let { b ->
        AlertDialog(onDismissRequest = { if (!busy) preview = null }, title = { Text("确认恢复这份备份？") },
            text = { Text("${b.lab.actions.size} 个行动 · ${b.lab.records.size} 条实验\n${b.lab.favorites.size} 个收藏 · ${b.lab.archived.size} 个归档\n${if (b.lab.session != null) "包含进行中或待反馈行动" else "没有进行中行动"}\n\n将替换当前业务数据、聊天、档案和偏好。导入前会保留一份本机副本，可撤销；重复导入不会增加重复记录。") },
            confirmButton = { TextButton(enabled = !busy, onClick = { restore(b) }) { Text("确认恢复") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { preview = null }) { Text("取消") } })
    }
    if (undoConfirm) AlertDialog(onDismissRequest = { if (!busy) undoConfirm = false },
        title = { Text("恢复导入前的数据？") }, text = { Text("当前数据将由导入前的副本替换。当前这份也会保留为下一次可恢复的副本。") },
        confirmButton = { TextButton(enabled = !busy, onClick = { restore(null, true) }) { Text("恢复") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { undoConfirm = false }) { Text("取消") } })
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false },
        title = { Text("删除本机恢复副本？") }, text = { Text("这会删除用于撤销导入的本机副本；当前记录和你导出的文件保留。") },
        confirmButton = { TextButton(onClick = {
            runCatching { BackupManager.deleteUndo(ctx) }.onSuccess { undoAvailable = false; message = "本机恢复副本已删除。" }
                .onFailure { message = it.message ?: "删除失败" }
            deleteConfirm = false
        }) { Text("删除副本") } },
        dismissButton = { TextButton({ deleteConfirm = false }) { Text("取消") } })
}
