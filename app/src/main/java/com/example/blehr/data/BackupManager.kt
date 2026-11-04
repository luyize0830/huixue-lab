package com.example.blehr.data

import android.content.Context
import android.util.AtomicFile
import com.example.blehr.logic.*
import java.io.File

/** Import journal restores the previous complete snapshot if a process dies between preference commits. */
object BackupManager {
    private fun before(ctx: Context) = AtomicFile(File(ctx.filesDir, "before-import.json"))
    private fun marker(ctx: Context) = AtomicFile(File(ctx.filesDir, "import-pending"))
    private fun write(file: AtomicFile, bytes: ByteArray) {
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }

    @Synchronized fun capture(ctx: Context): BackupData = BackupData(Store(ctx).load(DefaultPool.items),
        BackupCodec.allowed.mapValues { (name, keys) ->
            ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all.filter { (key, _) -> key in keys }
                .mapValues { checkNotNull(it.value) }
        }, System.currentTimeMillis())

    private fun apply(ctx: Context, data: BackupData) {
        BackupCodec.allowed.forEach { (name, keys) ->
            val editor = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            keys.keys.forEach { editor.remove(it) }
            data.preferences.getValue(name).forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    else -> error("备份设置的内容不正确")
                }
            }
            // Clear transient messages so importing an old backup cannot trigger yesterday's reminder.
            if (name == "huixue_nudge") editor.remove("pending_title").remove("pending_body")
            check(editor.commit()) { "设置保存失败" }
        }
        InitiativeStore.clearPending(ctx)
        Store(ctx).save(data.lab)
    }

    @Synchronized fun recover(ctx: Context): Boolean {
        if (!marker(ctx).baseFile.exists()) return false
        val old = BackupCodec.decode(before(ctx).readFully().toString(Charsets.UTF_8))
        apply(ctx, old)
        marker(ctx).delete()
        return true
    }

    @Synchronized fun restore(ctx: Context, imported: BackupData): LabData {
        check(Store(ctx).load(DefaultPool.items).session == null) { "请先完成或取消进行中的行动，再导入备份" }
        // Revalidate all inputs before touching persistent data.
        val validated = BackupCodec.decode(BackupCodec.encode(imported))
        val previous = BackupCodec.encode(capture(ctx)).toByteArray(Charsets.UTF_8)
        write(before(ctx), previous)
        write(marker(ctx), byteArrayOf(1))
        val next = validated.copy(lab = validated.lab.copy(session = validated.lab.session?.copy(boot = -1, elapsedDeadline = 0)))
        try {
            apply(ctx, next)
            marker(ctx).delete()
        } catch (e: Exception) {
            recover(ctx)
            throw e
        }
        return next.lab
    }

    fun hasUndo(ctx: Context): Boolean = before(ctx).baseFile.exists()

    @Synchronized fun undo(ctx: Context): LabData {
        // restore() swaps the snapshots, so repeated undo can itself be undone.
        val old = BackupCodec.decode(before(ctx).readFully().toString(Charsets.UTF_8))
        return restore(ctx, old)
    }

    @Synchronized fun deleteUndo(ctx: Context) {
        check(!marker(ctx).baseFile.exists()) { "恢复尚未完成，请重新打开软件" }
        before(ctx).delete()
    }
}
