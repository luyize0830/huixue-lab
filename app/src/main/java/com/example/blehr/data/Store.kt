package com.example.blehr.data

import android.content.Context
import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ExperimentRecord
import org.json.JSONArray
import org.json.JSONObject
import com.example.blehr.logic.LabData
import com.example.blehr.logic.LabCodec

/**
 * One atomic business snapshot; legacy records/pool migrate only after a successful commit.
 * User documents and cross-device backups are managed separately by BackupManager.
 */
class Store(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("huixue_lab", Context.MODE_PRIVATE)

    fun load(defaults: List<ActionItem>): LabData {
        sp.getString("snapshot_v2", null)?.let { return LabCodec.decode(JSONObject(it)) }
        val actions = loadPool() ?: defaults
        val records = loadRecords().map { r -> r.copy(actionSnapshot = actions.firstOrNull { it.id == r.actionId }) }
        return LabData(actions, records).also(::save)
    }

    /** One committed value keeps records, catalog and active session consistent after process death. */
    fun save(data: LabData) {
        val json = LabCodec.encode(data)
        LabCodec.decode(json) // Never persist a snapshot which the next launch cannot read.
        check(sp.edit().putString("snapshot_v2", json.toString())
            .remove(KEY_RECORDS).remove(KEY_POOL).commit()) { "保存失败，请检查手机可用空间后重试" }
    }

    // ---------------- 实验记录 ----------------

    fun loadRecords(): List<ExperimentRecord> {
        val raw = sp.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val rating = o.optInt("rating", 0)
                if (rating !in 1..5) return@mapNotNull null
                if (o.optString("actionId").isBlank() || o.optString("actionId").length > 100) return@mapNotNull null
                ExperimentRecord(
                    actionId = o.optString("actionId"),
                    band = runCatching { EnergyBand.valueOf(o.optString("band")) }
                        .getOrElse { EnergyBand.MID },
                    hrBefore = if (o.isNull("hrBefore")) null else o.optInt("hrBefore").takeIf { it in 1..300 },
                    hrAfter = if (o.isNull("hrAfter")) null else o.optInt("hrAfter").takeIf { it in 1..300 },
                    rating = rating,
                    ts = o.optLong("ts").coerceAtLeast(0)
                )
            }
        }.getOrElse { emptyList() }
    }

    // ---------------- 回血池（用户自定义行动） ----------------

    fun loadPool(): List<ActionItem>? {
        val raw = sp.getString(KEY_POOL, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ActionItem(
                    id = o.optString("id"),
                    emoji = o.optString("emoji", "✨"),
                    title = o.optString("title"),
                    minutes = o.optInt("minutes", 5),
                    category = runCatching { ActionCategory.valueOf(o.optString("category")) }
                        .getOrElse { ActionCategory.PLAY },
                    cue = o.optString("cue", "")
                )
            }.filter { runCatching { LabCodec.action(LabCodec.actionJson(it)) }.isSuccess }.distinctBy { it.id }
        }.getOrElse { null }
    }

    private companion object {
        const val KEY_RECORDS = "records_v1"
        const val KEY_POOL = "pool_v1"
    }
}
