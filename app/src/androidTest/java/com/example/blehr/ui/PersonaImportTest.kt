package com.example.blehr.ui

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blehr.data.PersonaDocumentReader
import com.example.blehr.data.PersonaStore
import com.example.blehr.logic.PersonaDocument
import com.example.blehr.logic.PersonaPreset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PersonaImportTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "persona_test_${UUID.randomUUID()}_"
    private val names = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = prefix + name
            names += isolated
            return base.getSharedPreferences(isolated, mode)
        }
    }

    @After fun cleanup() { names.forEach { base.deleteSharedPreferences(it) } }

    @Test fun previewShowsFileAndRequiresConfirmationToApply() {
        val document = PersonaDocument("口吻.md", "先接住她的话。")
        val showPreview = mutableStateOf(true)
        var draft = "原有草稿"
        compose.setContent {
            TextButton(onClick = { showPreview.value = true }) { Text("再次预览") }
            if (showPreview.value) {
                PersonaDocumentPreview(
                    document = document,
                    onConfirm = {
                        draft = document.text
                        showPreview.value = false
                    },
                    onCancel = { showPreview.value = false }
                )
            }
        }

        compose.onNodeWithText(document.fileName).assertIsDisplayed()
        compose.onNodeWithText("${document.text.length} 字符").assertIsDisplayed()
        compose.onNodeWithText("尚未保存，确认后放入编辑框。").assertIsDisplayed()
        compose.onNodeWithTag("persona-preview-body").assertTextEquals(document.text)
        compose.onNodeWithTag("persona-import-cancel").performClick()
        compose.runOnIdle { assertEquals("原有草稿", draft) }
        compose.onNodeWithText("再次预览").performClick()
        compose.onNodeWithTag("persona-import-confirm").performClick()
        compose.runOnIdle { assertEquals(document.text, draft) }
    }

    @Test fun overlongLegacySettingsRequireShorteningAndDirtyBackCanBeCanceled() {
        val old = "字".repeat(PersonaDocument.MAX_TEXT_CHARS + 1)
        PersonaStore.setPresetId(context, PersonaPreset.CUSTOM_ID)
        PersonaStore.setCustom(context, old)
        var backs = 0
        compose.setContent {
            val activityResults = requireNotNull(LocalActivityResultRegistryOwner.current)
            val backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides activityResults,
                LocalOnBackPressedDispatcherOwner provides backDispatcher
            ) {
                PersonaScreen(
                    initialPresetId = PersonaStore.presetId(context),
                    initialCustom = PersonaStore.custom(context),
                    onSave = { presetId, custom -> PersonaStore.save(context, presetId, custom) },
                    onReset = { PersonaStore.reset(context) },
                    onBack = { backs++ }
                )
            }
        }

        compose.onNodeWithTag("persona-character-count").performScrollTo()
            .assertTextEquals("${old.length} / ${PersonaDocument.MAX_TEXT_CHARS} 字符")
        compose.onNodeWithTag("persona-save").performScrollTo().performClick()
        assertEquals(old, PersonaStore.custom(context))
        assertFalse(PersonaStore.hasUndo(context))

        compose.onNodeWithTag("persona-custom").performScrollTo().performTextReplacement("有控制字符\u0001")
        compose.onNodeWithTag("persona-save").performScrollTo().performClick()
        compose.onNodeWithText("设定内容包含不支持的控制字符").performScrollTo().assertIsDisplayed()
        assertEquals(old, PersonaStore.custom(context))

        compose.onNodeWithTag("persona-custom").performScrollTo().performTextReplacement("新的草稿")
        compose.onNodeWithText("← 返回").performScrollTo().performClick()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.runOnIdle { assertEquals(0, backs) }
        assertEquals(old, PersonaStore.custom(context))

        compose.onNodeWithTag("persona-save").performScrollTo().performClick()
        assertEquals("新的草稿", PersonaStore.custom(context))
        assertTrue(PersonaStore.hasUndo(context))
        compose.runOnIdle { assertEquals(0, backs) }
    }

    @Test fun undoRestoresTheMostRecentSaveAndConsumesTheSnapshot() {
        PersonaStore.save(context, PersonaPreset.CUSTOM_ID, "最早的设定")
        PersonaStore.save(context, "warm", "保留的草稿")
        PersonaStore.reset(context)
        PersonaStore.reset(context)

        assertEquals(PersonaPreset.DEFAULT_ID, PersonaStore.presetId(context))
        assertEquals("", PersonaStore.custom(context))
        assertTrue(PersonaStore.hasUndo(context))
        PersonaStore.undo(context)
        assertEquals("warm", PersonaStore.presetId(context))
        assertEquals("保留的草稿", PersonaStore.custom(context))
        assertFalse(PersonaStore.hasUndo(context))
        assertTrue(runCatching { PersonaStore.undo(context) }.isFailure)
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun selectedPreviewSurvivesSavedStateRestorationWithoutSaving() {
        val file = File.createTempFile("persona-test-", ".txt", base.cacheDir)
        try {
            file.writeText("一句话就够。")
            PersonaStore.save(context, PersonaPreset.CUSTOM_ID, "原有设定")
            val uri = Uri.parse("content://persona-test/document/1")
            val documentContext = documentContext(file, "口吻.txt")
            val registry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?
                ) {
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(uri))
                }
            }
            val activityResults = object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }
            val restoration = StateRestorationTester(compose)
            restoration.setContent {
                val backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
                CompositionLocalProvider(
                    LocalContext provides documentContext,
                    LocalActivityResultRegistryOwner provides activityResults,
                    LocalOnBackPressedDispatcherOwner provides backDispatcher
                ) {
                    PersonaScreen(
                        initialPresetId = PersonaStore.presetId(context),
                        initialCustom = PersonaStore.custom(context),
                        onSave = { presetId, custom -> PersonaStore.save(context, presetId, custom) },
                        onReset = { PersonaStore.reset(context) },
                        onBack = {}
                    )
                }
            }
            compose.onNodeWithTag("persona-import").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("persona-preview-body").fetchSemanticsNodes().isNotEmpty()
            }
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("口吻.txt").assertIsDisplayed()
            compose.onNodeWithTag("persona-preview-body").assertTextEquals("一句话就够。")
            compose.onNodeWithTag("persona-import-confirm").performClick()
            compose.onNodeWithTag("persona-custom").performScrollTo().assertTextEquals("一句话就够。")
            assertEquals("原有设定", PersonaStore.custom(context))
            compose.onNodeWithTag("persona-undo-import").performScrollTo().performClick()
            compose.onNodeWithTag("persona-custom").performScrollTo().assertTextEquals("原有设定")
        } finally {
            file.delete()
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun androidContentReaderAcceptsDocxAndRejectsItsDtd() {
        val file = File.createTempFile("persona-test-", ".docx", base.cacheDir)
        val documentContext = documentContext(file, "口吻.docx")
        val uri = Uri.parse("content://persona-test/document/1")
        fun writeDocx(text: String, prolog: String = "") {
            val xml = """$prolog
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:body>
                </w:document>
            """.trimIndent()
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        try {
            writeDocx("一句话就够。")
            assertEquals(PersonaDocument("口吻.docx", "一句话就够。"), PersonaDocumentReader.read(documentContext, uri))
            writeDocx("&demo;", "<!DOCTYPE w:document [<!ENTITY demo '不应被解析'>]>")
            val error = runCatching { PersonaDocumentReader.read(documentContext, uri) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException && error.message.orEmpty().contains("DTD"))
        } finally {
            file.delete()
        }
    }

    private fun documentContext(file: File, displayName: String): Context {
        val provider = object : ContentProvider() {
            override fun onCreate(): Boolean = true
            override fun getType(uri: Uri): String = "application/octet-stream"
            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?
            ) = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(displayName)) }

            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("read only")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = error("read only")
            override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = error("read only")
        }
        val resolver = ContentResolver.wrap(provider)
        return object : ContextWrapper(context) {
            override fun getContentResolver(): ContentResolver = resolver
        }
    }
}
