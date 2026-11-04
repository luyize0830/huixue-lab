package com.example.blehr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.data.Repo
import com.example.blehr.model.*
import java.util.UUID

@Composable
fun ActionManagerScreen(onBack: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf<ActionItem?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    fun change(block: () -> Unit) {
        runCatching(block).onFailure { message = it.message ?: "保存失败，请重试" }
    }
    val items = Repo.allActions.filter {
        when (filter) {
            1 -> it.id in Repo.snapshot.favorites && it.id !in Repo.snapshot.archived
            2 -> it.id in Repo.snapshot.archived
            else -> it.id !in Repo.snapshot.archived
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        BackRow("管理行动", onBack)
        Text("收藏常做的，归档暂时不想做的。已完成记录保留当时的内容。", color = Look.InkSoft, fontSize = 13.sp)
        Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("新增行动") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("使用中", "收藏", "已归档").forEachIndexed { i, label ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
            }
        }
        if (message.isNotBlank()) Text(message, color = Look.Clay)
        if (items.isEmpty()) Text("这里还没有行动，试试新增一个或切换分类。", color = Look.InkSoft)
        items.forEach { a ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryTag(a.category, 34.dp)
                        Column {
                            Text(a.title, color = Look.Ink)
                            Text("${a.minutes} 分钟 · ${a.category.label}", fontSize = 12.sp, color = Look.InkSoft)
                        }
                    }
                    Text(a.cue, fontSize = 13.sp, color = Look.InkSoft)
                    Row {
                        TextButton({ change { Repo.favorite(a.id) } }) {
                            Text(if (a.id in Repo.snapshot.favorites) "取消收藏" else "收藏")
                        }
                        TextButton({ editing = a; editorOpen = true }) { Text("编辑") }
                        TextButton({ change { Repo.archive(a.id) } }) {
                            Text(if (a.id in Repo.snapshot.archived) "恢复" else "归档")
                        }
                    }
                }
            }
        }
    }
    if (editorOpen) ActionEditor(editing, onDismiss = { editorOpen = false }) { action ->
        runCatching { if (editing == null) Repo.addAction(action) else Repo.editAction(action) }
            .onSuccess { editorOpen = false }
            .onFailure { message = it.message ?: "保存失败，请重试" }
    }
}

@Composable
private fun ActionEditor(initial: ActionItem?, onDismiss: () -> Unit, onSave: (ActionItem) -> Unit) {
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title ?: "") }
    var cue by rememberSaveable(initial?.id) { mutableStateOf(initial?.cue ?: "") }
    var minutes by rememberSaveable(initial?.id) { mutableStateOf(initial?.minutes ?: 3) }
    var category by rememberSaveable(initial?.id) { mutableStateOf(initial?.category ?: ActionCategory.CALM) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增行动" else "编辑行动") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("行动名称") }, singleLine = true)
                OutlinedTextField(cue, { cue = it.take(500) }, label = { Text("第一步怎么做") }, minLines = 2)
                Text("时长：$minutes 分钟")
                Slider(minutes.toFloat(), { minutes = it.toInt() }, valueRange = 1f..10f, steps = 8)
                ActionCategory.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { c ->
                            FilterChip(category == c, { category = c }, label = { Text(c.label) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank() && cue.isNotBlank(), onClick = {
            onSave(ActionItem(initial?.id ?: UUID.randomUUID().toString(), initial?.emoji ?: "✨",
                title.trim(), minutes, category, cue.trim()))
        }) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } })
}
