package com.example.blehr.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.data.PersonaDocumentReader
import com.example.blehr.data.PersonaStore
import com.example.blehr.logic.PersonaDocument
import com.example.blehr.logic.PersonaPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.Serializable

private data class PersonaDraft(val presetId: String, val custom: String) : Serializable

private data class PersonaFeedback(val text: String, val isError: Boolean = false)

private val documentSaver = listSaver<PersonaDocument?, String>(
    save = { document -> document?.let { listOf(it.fileName, it.text) }.orEmpty() },
    restore = { values -> PersonaDocument(values[0], values[1]) }
)

private enum class PersonaConfirmation(val title: String, val message: String, val action: String) {
    LEAVE("放弃未保存的修改？", "返回后会保留上次保存的性格。", "放弃并返回"),
    RESET("恢复默认性格？", "这会清空编辑框，并立即保存默认性格。", "确认恢复"),
    UNDO("撤销上次保存？", "这会恢复上次保存前的性格，并替换当前编辑框中的内容。", "确认撤销")
}

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
    val context = LocalContext.current
    val limit = PersonaDocument.MAX_TEXT_CHARS
    val editorRequester = remember { BringIntoViewRequester() }
    var draft by rememberSaveable { mutableStateOf(PersonaDraft(initialPresetId, initialCustom)) }
    var saved by rememberSaveable { mutableStateOf(draft) }
    var beforeImport by rememberSaveable { mutableStateOf<PersonaDraft?>(null) }
    var canUndoSaved by remember { mutableStateOf(PersonaStore.hasUndo(context)) }
    var preview by rememberSaveable(stateSaver = documentSaver) { mutableStateOf<PersonaDocument?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    val busy = pickerOpen || pendingUri != null
    var confirmation by rememberSaveable { mutableStateOf<PersonaConfirmation?>(null) }
    var feedback by remember { mutableStateOf<PersonaFeedback?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var rejectedInput by remember { mutableStateOf(false) }
    var showImportedDraft by remember { mutableStateOf(false) }

    fun edit(next: PersonaDraft) {
        draft = next
        rejectedInput = false
        feedback = null
    }

    fun persist(success: String, operation: () -> PersonaDraft) {
        try {
            val next = operation()
            edit(next)
            saved = next
            beforeImport = null
            canUndoSaved = PersonaStore.hasUndo(context)
            feedback = PersonaFeedback(success)
        } catch (_: Exception) {
            feedback = PersonaFeedback("未能更新已保存的性格，请重试。", isError = true)
        }
    }

    val requestBack: () -> Unit = {
        if (!busy) {
            if (draft == saved) onBack() else confirmation = PersonaConfirmation.LEAVE
        }
    }
    BackHandler(onBack = requestBack)

    val openDocument = remember {
        object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent =
                super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
    }
    val importer = rememberLauncherForActivityResult(openDocument) { uri ->
        pickerOpen = false
        pendingUri = uri
    }
    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        try {
            preview = withContext(Dispatchers.IO) { PersonaDocumentReader.read(context, uri) }
        } catch (error: CancellationException) {
            // Activity recreation can resume the selection in the new composition.
            throw error
        } catch (error: IllegalArgumentException) {
            importError = error.message ?: "设定文档格式有误，请重新选择。"
        } catch (_: IOException) {
            importError = "无法读取所选文件，请重新选择。"
        }
        pendingUri = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Look.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BackRow("小夜的性格", requestBack)
        Spacer(Modifier.height(4.dp))
        Text(
            "她怎么说话，由你定。",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Look.Ink
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "保存后的设定会随聊天消息一起发给你接入的模型。",
            fontSize = 12.sp,
            color = Look.InkSoft,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().testTag("persona-import"),
            enabled = !busy,
            onClick = {
                pickerOpen = true
                importError = null
                try {
                    // Markdown files can be labeled with an unknown MIME type by document providers.
                    importer.launch(arrayOf("*/*"))
                } catch (_: ActivityNotFoundException) {
                    pickerOpen = false
                    importError = "无法打开系统文件选择器。"
                }
            }
        ) { Text(if (busy) "正在读取设定文档…" else "导入设定文档") }
        Text("支持 TXT、MD、DOCX，最大 512 KB。", fontSize = 12.sp, color = Look.InkSoft)
        importError?.let {
            Text(it, modifier = Modifier.testTag("persona-import-error"), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))

        PersonaPreset.all.forEach { p ->
            PresetRow(
                title = p.title,
                hint = p.hint,
                sample = p.sample,
                selected = draft.presetId == p.id,
                enabled = !busy,
                onClick = { edit(draft.copy(presetId = p.id)) }
            )
            Spacer(Modifier.height(10.dp))
        }

        if (PersonaPreset.isCustom(draft.presetId)) {
            LaunchedEffect(showImportedDraft) {
                if (showImportedDraft) {
                    editorRequester.bringIntoView()
                    showImportedDraft = false
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "你自己写的。想怎么写就怎么写，比如「说话像东北大姨」。",
                fontSize = 12.sp,
                color = Look.InkSoft
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.custom,
                onValueChange = {
                    if (it.length <= limit || it.length < draft.custom.length) {
                        edit(draft.copy(custom = it))
                    } else {
                        rejectedInput = true
                        feedback = null
                    }
                },
                enabled = !busy,
                isError = rejectedInput || draft.custom.length > limit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .bringIntoViewRequester(editorRequester)
                    .testTag("persona-custom"),
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
            Text(
                "${draft.custom.length} / $limit 字符",
                modifier = Modifier.testTag("persona-character-count"),
                fontSize = 12.sp,
                color = Look.InkSoft
            )
            if (draft.custom.length > limit) {
                Text("设定最多 $limit 个字符，请删减后再保存。", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            } else if (rejectedInput) {
                Text("这次输入超过 $limit 个字符，请缩短后重新输入。", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(14.dp))
        }

        beforeImport?.let { previous ->
            TextButton(
                modifier = Modifier.testTag("persona-undo-import"),
                enabled = !busy,
                onClick = {
                    edit(previous)
                    beforeImport = null
                }
            ) { Text("恢复导入前的草稿") }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("persona-save")
                .clip(RoundedCornerShape(14.dp))
                .background(Look.Accent)
                .clickable(enabled = !busy) {
                    if (draft.custom.length > limit) {
                        feedback = PersonaFeedback("请在「我自己写」中将设定删减到 $limit 个字符以内再保存。", isError = true)
                        return@clickable
                    }
                    val validated = try {
                        val text = draft.custom
                        if (text.isBlank() && text.none { Character.isISOControl(it) && it !in "\r\n\t" }) {
                            ""
                        } else {
                            PersonaDocument.validateText(text)
                        }
                    } catch (error: IllegalArgumentException) {
                        feedback = PersonaFeedback(error.message ?: "设定内容有误，请修改后保存。", isError = true)
                        return@clickable
                    }
                    persist("性格已保存，下次使用模型聊天时生效。") {
                        onSave(draft.presetId, validated)
                        draft.copy(custom = validated)
                    }
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
                .testTag("persona-reset")
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Look.Hair, RoundedCornerShape(14.dp))
                .clickable(enabled = !busy) { confirmation = PersonaConfirmation.RESET },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("恢复默认", fontSize = 14.sp, color = Look.InkSoft)
        }

        if (canUndoSaved) {
            TextButton(
                modifier = Modifier.testTag("persona-undo-save"),
                enabled = !busy,
                onClick = { confirmation = PersonaConfirmation.UNDO }
            ) { Text("撤销上次保存") }
        }

        feedback?.let {
            Spacer(Modifier.height(12.dp))
            Text(it.text, fontSize = 12.sp, color = if (it.isError) MaterialTheme.colorScheme.error else Look.Olive)
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

    preview?.let { document ->
        PersonaDocumentPreview(
            document = document,
            onConfirm = {
                val imported = PersonaDraft(PersonaPreset.CUSTOM_ID, document.text)
                if (draft != imported) beforeImport = draft
                edit(imported)
                preview = null
                showImportedDraft = true
                feedback = PersonaFeedback("已放入编辑框。点「保存」后，设定才会生效。")
            },
            onCancel = { preview = null }
        )
    }

    confirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(pending.title) },
            text = { Text(pending.message) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    when (pending) {
                        PersonaConfirmation.LEAVE -> onBack()
                        PersonaConfirmation.RESET -> persist("已经回到默认的小夜。") {
                            onReset()
                            PersonaDraft(PersonaPreset.DEFAULT_ID, "")
                        }
                        PersonaConfirmation.UNDO -> persist("已恢复到上次保存前的性格。") {
                            PersonaStore.undo(context)
                            PersonaDraft(PersonaStore.presetId(context), PersonaStore.custom(context))
                        }
                    }
                }) { Text(pending.action) }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
                    Text(if (pending == PersonaConfirmation.LEAVE) "继续编辑" else "取消")
                }
            }
        )
    }
}

@Composable
internal fun PersonaDocumentPreview(
    document: PersonaDocument,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("预览设定文档") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(document.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${document.text.length} 字符")
                Text("尚未保存，确认后放入编辑框。")
                Text(
                    document.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag("persona-preview-body")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("persona-import-confirm")) { Text("放入编辑框") }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("persona-import-cancel")) { Text("取消") }
        }
    )
}

@Composable
private fun PresetRow(
    title: String,
    hint: String,
    sample: String,
    selected: Boolean,
    enabled: Boolean,
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
            .clickable(enabled = enabled, onClick = onClick)
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
