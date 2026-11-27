package com.example.blehr.logic

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaDocumentTest {
    @Test
    fun `读取大写TXT并去掉BOM和首尾空白`() {
        val file = PersonaDocument.read("小夜.TXT", "\uFEFF   说话慢一点。\r\n\r\n先听我讲完。\r\n ".toByteArray())
        assertEquals("小夜.TXT", file.fileName)
        assertEquals("说话慢一点。\n\n先听我讲完。", file.text)
    }

    @Test
    fun `Markdown保留标题和缩进`() {
        val markdown = "# 人设\n\n- 称呼\n\t- 小夜"
        assertEquals(markdown, PersonaDocument.read("自己写.mD", markdown.toByteArray()).text)
    }

    @Test
    fun `Word正文合并短片段并保留段落换行和制表符`() {
        val xml = document(
            """
            <x:p><x:r><x:t>小</x:t></x:r><x:r><x:t>夜 &amp; 我</x:t><x:br/><x:t>第二行</x:t><x:tab/><x:t>对齐</x:t></x:r></x:p>
            <x:p/>
            <x:p><x:hyperlink r:id="rId1"><x:r><x:t>显示文字</x:t></x:r></x:hyperlink></x:p>
            """.trimIndent()
        )
        val bytes = zip(
            "word/media/image1.png" to byteArrayOf(1, 2, 3),
            BODY_PATH to xml.toByteArray(),
            "word/_rels/document.xml.rels" to "https://example.invalid/ignored".toByteArray(),
            "word/header1.xml" to document("<x:p><x:r><x:t>不用页眉</x:t></x:r></x:p>").toByteArray()
        )
        assertEquals("小夜 & 我\n第二行\t对齐\n\n显示文字", PersonaDocument.read("设定.DoCx", bytes).text)
    }

    @Test
    fun `拒绝非法UTF8而不替换乱码`() {
        rejected("设定.txt", byteArrayOf(0xc3.toByte(), 0x28), "UTF-8")
    }

    @Test
    fun `拒绝改名的二进制与PDF`() {
        rejected("改名.md", byteArrayOf(0x50, 0x4b, 0x03, 0x04), "控制字符")
        rejected("改名.txt", "%PDF-1.7".toByteArray(), "PDF")
    }

    @Test
    fun `拒绝PDF旧版Word和无后缀文件`() {
        for (name in listOf("设定.pdf", "设定.DOC", "设定")) {
            rejected(name, "设定".toByteArray(), "TXT、MD 或 DOCX")
        }
    }

    @Test
    fun `拒绝空文本与只有空段落的Word文件`() {
        rejected("设定.txt", "\uFEFF\r\n\t  ".toByteArray(), "没有正文")
        rejected("设定.docx", word("<x:p/>"), "没有正文")
    }

    @Test
    fun `超过原始文件大小上限时不尝试格式解析`() {
        rejected("设定.docx", ByteArray(PersonaDocument.MAX_FILE_BYTES + 1), "512 KB")
    }

    @Test
    fun `接受文本上限且拒绝多一个字符`() {
        val max = "好".repeat(PersonaDocument.MAX_TEXT_CHARS)
        assertEquals(max, PersonaDocument.read("设定.md", max.toByteArray()).text)
        rejected("设定.md", (max + "好").toByteArray(), "6000 字符")
        rejected("设定.docx", word("<x:p><x:r><x:t>${max}好</x:t></x:r></x:p>"), "6000 字符")
    }

    @Test
    fun `手输验证规范化文本并拒绝控制字符`() {
        assertEquals("温柔\n\n随意\t简短", PersonaDocument.validateText(" 温柔\r\n\r随意\t简短 "))
        for (text in listOf("设定\u0000", "设定\u007f", "设定\u0085")) {
            val failure = assertThrows(IllegalArgumentException::class.java) { PersonaDocument.validateText(text) }
            assertTrue(failure.message.orEmpty().contains("控制字符"))
        }
    }

    @Test
    fun `正文字少但XML解压超过上限仍拒绝`() {
        val payload = " ".repeat(PersonaDocument.MAX_XML_BYTES)
        val bytes = word("<!--$payload--><x:p><x:r><x:t>短设定</x:t></x:r></x:p>")
        assertTrue(bytes.size < PersonaDocument.MAX_FILE_BYTES)
        rejected("设定.docx", bytes, "正文 XML 不能超过 1 MB")
    }

    @Test
    fun `拒绝包含外部实体的Word文件`() {
        val xml = """
            <!DOCTYPE x:document [<!ENTITY remote SYSTEM "file:///nonexistent-persona-file">]>
            ${document("<x:p><x:r><x:t>&remote;</x:t></x:r></x:p>")}
        """.trimIndent()
        rejected("设定.docx", zip(BODY_PATH to xml.toByteArray()), "DTD")
    }

    @Test
    fun `即使没有外部引用也禁止DTD`() {
        val xml = "<!DOCTYPE x:document>\n" + document("<x:p><x:r><x:t>短设定</x:t></x:r></x:p>")
        rejected("设定.docx", zip(BODY_PATH to xml.toByteArray()), "DTD")
    }

    @Test
    fun `拒绝XML语法错误`() {
        rejected("设定.docx", word("<x:p><x:r>没有闭合段落"), "正文 XML 无效")
    }

    @Test
    fun `拒绝无正文条目和无Word正文节点`() {
        rejected("设定.docx", zip("word/header1.xml" to "标题".toByteArray()), "找不到正文")
        rejected("设定.docx", zip(BODY_PATH to "<root>误改扩展名</root>".toByteArray()), "找不到正文")
    }

    @Test
    fun `忽略压缩包外观并拒绝目录丢失或条目校验损坏`() {
        val good = word("<x:p><x:r><x:t>一段设定</x:t></x:r></x:p>")
        rejected("设定.docx", good.copyOf(good.size - 22), "压缩文件")
        val stored = zip(BODY_PATH to document("<x:p><x:r><x:t>一段设定</x:t></x:r></x:p>").toByteArray(), stored = true)
        // Flip one byte in the local file data while preserving the declared CRC and directory.
        stored[30 + BODY_PATH.length] = 0
        rejected("设定.docx", stored, "已损坏")
    }

    @Test
    fun `普通正文后的小体积压缩炸弹也受扫描上限约束`() {
        val bytes = zip(
            BODY_PATH to document("<x:p><x:r><x:t>可读正文</x:t></x:r></x:p>").toByteArray(),
            "word/media/image1.bin" to ByteArray(9 * 1024 * 1024)
        )
        assertTrue(bytes.size < PersonaDocument.MAX_FILE_BYTES)
        rejected("设定.docx", bytes, "解压后的文件过大")
    }

    private fun rejected(name: String, bytes: ByteArray, expectedMessage: String) {
        val error = assertThrows(IllegalArgumentException::class.java) { PersonaDocument.read(name, bytes) }
        assertTrue(error.message, error.message.orEmpty().contains(expectedMessage))
    }

    private fun word(body: String): ByteArray = zip(BODY_PATH to document(body).toByteArray())

    private fun document(body: String): String =
        """<x:document xmlns:x="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><x:body>$body</x:body></x:document>"""

    private fun zip(vararg entries: Pair<String, ByteArray>, stored: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                val entry = ZipEntry(name)
                if (stored) {
                    entry.method = ZipEntry.STORED
                    entry.size = content.size.toLong()
                    entry.crc = java.util.zip.CRC32().apply { update(content) }.value
                }
                zip.putNextEntry(entry)
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private companion object {
        const val BODY_PATH = "word/document.xml"
    }
}
