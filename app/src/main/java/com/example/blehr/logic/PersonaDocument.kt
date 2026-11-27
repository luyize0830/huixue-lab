package com.example.blehr.logic

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2

data class PersonaDocument(val fileName: String, val text: String) {
    companion object {
        const val MAX_FILE_BYTES = 512 * 1024
        const val MAX_TEXT_CHARS = 6000
        const val MAX_XML_BYTES = 1024 * 1024

        private const val MAX_EXPANDED_BYTES = 8 * MAX_XML_BYTES
        private const val DOCUMENT_XML = "word/document.xml"
        private const val ZIP_END_BYTES = 22
        private const val ZIP_END_SIGNATURE = 0x06054b50L
        private val wordNamespaces = setOf(
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
            "http://purl.oclc.org/ooxml/wordprocessingml/main"
        )

        fun read(fileName: String, bytes: ByteArray): PersonaDocument {
            require(bytes.size <= MAX_FILE_BYTES) { "设定文件不能超过 512 KB" }
            val raw = when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                "txt", "md" -> readUtf8(bytes)
                "docx" -> readWordText(readDocumentXml(bytes))
                else -> throw IllegalArgumentException("请选择 TXT、MD 或 DOCX 格式的设定文件")
            }
            return PersonaDocument(fileName, validateText(raw))
        }

        fun validateText(text: String): String {
            val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
            require(normalized.none { Character.isISOControl(it) && it !in "\n\t" }) {
                "设定内容包含不支持的控制字符"
            }
            val trimmed = normalized.trim()
            require(trimmed.isNotEmpty()) { "设定文档没有正文" }
            require(trimmed.length <= MAX_TEXT_CHARS) { "设定正文不能超过 6000 字符" }
            return trimmed
        }

        private fun readUtf8(bytes: ByteArray): String {
            val text = try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (error: CharacterCodingException) {
                throw IllegalArgumentException("TXT 或 MD 文件必须使用 UTF-8 编码", error)
            }
            require(!text.removePrefix("\uFEFF").startsWith("%PDF-")) {
                "文件包含 PDF 内容，请选择纯文本设定"
            }
            return text
        }

        private fun readDocumentXml(bytes: ByteArray): ByteArray {
            val expectedEntries = zipEntryCount(bytes)
            var document: ByteArray? = null
            var expanded = 0
            var entries = 0
            try {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entries++
                        val body = if (entry.name == DOCUMENT_XML) {
                            require(document == null && !entry.isDirectory) { "DOCX 正文条目无效" }
                            ByteArrayOutputStream()
                        } else null
                        // Read skipped entries through the same budget. closeEntry alone drains without a limit.
                        while (true) {
                            val count = zip.read(buffer)
                            if (count == -1) break
                            expanded += count
                            require(expanded <= MAX_EXPANDED_BYTES) { "DOCX 解压后的文件过大" }
                            if (body != null) {
                                require(body.size() + count <= MAX_XML_BYTES) { "DOCX 正文 XML 不能超过 1 MB" }
                                body.write(buffer, 0, count)
                            }
                        }
                        if (body != null) document = body.toByteArray()
                    }
                }
            } catch (error: IOException) {
                throw IllegalArgumentException("DOCX 压缩内容已损坏", error)
            }
            require(entries == expectedEntries) { "DOCX 压缩内容已损坏" }
            return requireNotNull(document) { "DOCX 中找不到正文" }
        }

        private fun zipEntryCount(bytes: ByteArray): Int {
            // ZipInputStream can read local entries even when a ZIP was cut off before its directory.
            val firstPossibleEnd = (bytes.size - ZIP_END_BYTES - 0xffff).coerceAtLeast(0)
            for (offset in bytes.size - ZIP_END_BYTES downTo firstPossibleEnd) {
                if (littleEndian(bytes, offset, 4) != ZIP_END_SIGNATURE ||
                    offset + ZIP_END_BYTES + littleEndian(bytes, offset + 20, 2) != bytes.size.toLong()
                ) continue
                val disk = littleEndian(bytes, offset + 4, 2)
                val directoryDisk = littleEndian(bytes, offset + 6, 2)
                val diskEntries = littleEndian(bytes, offset + 8, 2)
                val entries = littleEndian(bytes, offset + 10, 2)
                val directorySize = littleEndian(bytes, offset + 12, 4)
                val directoryOffset = littleEndian(bytes, offset + 16, 4)
                if (disk == 0L && directoryDisk == 0L && diskEntries == entries &&
                    directoryOffset + directorySize == offset.toLong()
                ) return entries.toInt()
            }
            throw IllegalArgumentException("无法读取 DOCX 压缩文件")
        }

        private fun littleEndian(bytes: ByteArray, offset: Int, size: Int): Long {
            var value = 0L
            for (index in size - 1 downTo 0) {
                value = (value shl 8) or (bytes[offset + index].toLong() and 0xff)
            }
            return value
        }

        private fun readWordText(xml: ByteArray): String {
            val handler = WordBody()
            try {
                val reader = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
                    .newSAXParser().xmlReader
                reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
                reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
                reader.entityResolver = handler
                reader.contentHandler = handler
                reader.errorHandler = handler
                reader.parse(InputSource(ByteArrayInputStream(xml)))
            } catch (error: ParserConfigurationException) {
                throw IllegalArgumentException("无法安全读取 DOCX 正文 XML", error)
            } catch (error: SAXException) {
                throw IllegalArgumentException("DOCX 正文 XML 无效或包含被禁用的 DTD", error)
            } catch (error: IOException) {
                throw IllegalArgumentException("无法读取 DOCX 正文 XML", error)
            }
            require(handler.foundBody) { "DOCX 中找不到正文" }
            return handler.text.toString()
        }

        private class WordBody : DefaultHandler2() {
            val text = StringBuilder()
            var foundBody = false
                private set
            private var depth = 0
            private var namespace = ""
            private var inBody = false
            private var textDepth = 0

            override fun startDTD(name: String?, publicId: String?, systemId: String?) {
                throw SAXException("不允许读取 DTD")
            }

            override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
                throw SAXException("不允许读取外部实体")
            }

            override fun resolveEntity(name: String?, publicId: String?, baseURI: String?, systemId: String?): InputSource {
                throw SAXException("不允许读取外部实体")
            }

            override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                depth++
                if (depth == 1 && localName == "document" && uri in wordNamespaces) namespace = uri
                if (namespace.isEmpty() || uri != namespace) return
                if (depth == 2 && localName == "body") {
                    foundBody = true
                    inBody = true
                }
                if (!inBody) return
                when (localName) {
                    "t" -> textDepth = depth
                    "br", "cr" -> text.append('\n')
                    "tab" -> text.append('\t')
                }
            }

            override fun characters(chars: CharArray, start: Int, length: Int) {
                if (textDepth == depth && textDepth != 0) text.append(chars, start, length)
            }

            override fun endElement(uri: String, localName: String, qName: String) {
                if (textDepth == depth) textDepth = 0
                if (inBody && uri == namespace) {
                    if (localName == "p") text.append('\n')
                    if (depth == 2 && localName == "body") inBody = false
                }
                depth--
            }
        }
    }
}
