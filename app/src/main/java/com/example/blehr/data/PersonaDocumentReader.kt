package com.example.blehr.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.blehr.logic.PersonaDocument
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

object PersonaDocumentReader {
    fun read(context: Context, uri: Uri): PersonaDocument {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) { "请通过系统文件选择器选择设定文档" }
        val resolver = context.contentResolver
        val fileName = displayName(resolver, uri)
            ?: uri.lastPathSegment?.substringAfterLast(':')?.let(::baseName)
            ?: "未命名文件"
        val bytes = try {
            val stream = resolver.openInputStream(uri) ?: throw IOException()
            stream.use(::readBounded)
        } catch (error: Exception) {
            throw IOException("无法读取所选文件，请重新选择", error)
        }
        requireNotNull(bytes) { "设定文件不能超过 512 KB" }
        return PersonaDocument.read(fileName, bytes)
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index)?.let(::baseName) else null
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun baseName(name: String): String? =
        name.substringAfterLast('/').substringAfterLast('\\').trim().ifBlank { null }

    private fun readBounded(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < PersonaDocument.MAX_FILE_BYTES) {
            val remaining = PersonaDocument.MAX_FILE_BYTES - output.size()
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count == -1) return output.toByteArray()
            if (count == 0) throw IOException()
            output.write(buffer, 0, count)
        }
        return if (input.read() == -1) output.toByteArray() else null
    }
}
