package com.example.blehr.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blehr.logic.*
import com.example.blehr.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Separate preferences and files ensure this suite cannot clear a tester's real data. */
@RunWith(AndroidJUnit4::class)
class BackupPersistenceTest {
    private lateinit var ctx: Context
    private lateinit var dir: File
    private val names = mutableSetOf<String>()
    private val action = ActionItem("test-water", "", "旧标题", 1, ActionCategory.FUEL, "先接水")
    @Before fun setup() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "isolated_${UUID.randomUUID()}_"
        dir = File(base.cacheDir, prefix).apply { mkdirs() }
        ctx = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = dir
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                names += prefix + name
                return base.getSharedPreferences(prefix + name, mode)
            }
        }
    }
    @After fun cleanup() {
        names.forEach { InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(it) }
        dir.deleteRecursively()
    }
    @Test fun legacyMigrationCommitsSnapshotBeforeRemovingOldKeys() {
        val prefs = ctx.getSharedPreferences("huixue_lab", 0)
        prefs.edit().putString("pool_v1", "[${LabCodec.actionJson(action)},${LabCodec.actionJson(action)},${LabCodec.actionJson(action.copy(id = "bad", minutes = 0))}]")
            .putString("records_v1", """[{"actionId":"test-water","band":"LOW","hrBefore":0,"hrAfter":null,"rating":4,"ts":1000},{"rating":4}]""").commit()
        val data = Store(ctx).load(DefaultPool.items)
        assertEquals(action, data.records.single().actionSnapshot)
        assertEquals(listOf(action), data.actions)
        assertNull(data.records.single().hrBefore)
        assertEquals(data, Store(ctx).load(DefaultPool.items))
        assertTrue(prefs.contains("snapshot_v2"))
        assertFalse(prefs.contains("records_v1"))
        assertFalse(prefs.contains("pool_v1"))
    }
    @Test fun pausedSessionAndFavoritesPersistTogether() {
        val session = ActionSession.start(action, EnergyBand.MID, 80, 1000, 1000, 1).pause(21000, 21000, 1)
        val data = LabData(listOf(action), favorites = setOf(action.id), session = session)
        Store(ctx).save(data)
        assertEquals(data, Store(ctx).load(emptyList()))
    }
    @Test fun credentialsAreNeverExportedOrReplacedOnImport() {
        ctx.getSharedPreferences("huixue_llm", 0).edit().putString("api_key", "local-test-value").commit()
        val backup = BackupManager.capture(ctx)
        assertFalse(BackupCodec.encode(backup).contains("local-test-value"))
        BackupManager.restore(ctx, backup)
        assertEquals("local-test-value", ctx.getSharedPreferences("huixue_llm", 0).getString("api_key", null))
    }
    @Test fun repeatedRestoreDoesNotDuplicateRecordsAndUndoRestoresPreviousData() {
        Store(ctx).save(LabData(listOf(action)))
        val first = BackupManager.capture(ctx)
        val record = ExperimentRecord(action.id, EnergyBand.LOW, null, null, 5, 1000, "one", action)
        val second = first.copy(lab = first.lab.copy(records = listOf(record)))
        BackupManager.restore(ctx, second)
        assertEquals(1, Store(ctx).load(emptyList()).records.size)
        BackupManager.restore(ctx, second)
        assertEquals(1, Store(ctx).load(emptyList()).records.size)
        BackupManager.restore(ctx, first)
        BackupManager.undo(ctx)
        assertEquals(1, Store(ctx).load(emptyList()).records.size)
    }
    @Test fun activeSessionPreventsReplacingItsFeedback() {
        val before = BackupManager.capture(ctx)
        val session = ActionSession.start(action, EnergyBand.MID, null, 1000, 1000, 1)
        Store(ctx).save(LabData(listOf(action), session = session))
        assertTrue(runCatching { BackupManager.restore(ctx, before) }.isFailure)
        assertEquals(session, Store(ctx).load(emptyList()).session)
    }
    @Test fun interruptedImportRecoversBothRecordsAndProfile() {
        ProfileStore.save(ctx, Profile(nickname = "导入前"))
        Store(ctx).save(LabData(listOf(action)))
        val old = BackupManager.capture(ctx)
        File(dir, "before-import.json").writeText(BackupCodec.encode(old))
        File(dir, "import-pending").writeText("pending")
        ProfileStore.save(ctx, Profile(nickname = "导入到一半"))
        Store(ctx).save(LabData(emptyList()))
        assertTrue(BackupManager.recover(ctx))
        assertEquals("导入前", ProfileStore.load(ctx).nickname)
        assertEquals(listOf(action), Store(ctx).load(emptyList()).actions)
        assertFalse(BackupManager.recover(ctx))
    }
}
