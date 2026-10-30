package com.example.blehr.data

import android.content.Context
import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ExperimentRecord
import org.json.JSONArray
import org.json.JSONObject

/**
 * 极简本地存储：SharedPreferences + JSON。
 *
 * 选型理由（写进技术研究报告）：本项目数据量小（每人每天几条实验记录），
 * 用 Room 属于过度设计；SharedPreferences 存 JSON 足够，且**全部数据留在本机**，
 * 天然满足"数据不上云、可一键删除"的隐私要求。
 */
class Store(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("huixue_lab", Context.MODE_PRIVATE)

    // ---------------- 实验记录 ----------------

    fun loadRecords(): List<ExperimentRecord> {
        val raw = sp.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val rating = o.optInt("rating", 0)
                if (rating <= 0) return@mapNotNull null
                ExperimentRecord(
                    actionId = o.optString("actionId"),
                    band = runCatching { EnergyBand.valueOf(o.optString("band")) }
                        .getOrElse { EnergyBand.MID },
                    hrBefore = if (o.isNull("hrBefore")) null else o.optInt("hrBefore"),
                    hrAfter = if (o.isNull("hrAfter")) null else o.optInt("hrAfter"),
                    rating = rating,
                    ts = o.optLong("ts")
                )
            }
        }.getOrElse { emptyList() }
    }

    fun saveRecords(list: List<ExperimentRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("actionId", r.actionId)
                    put("band", r.band.name)
                    put("hrBefore", r.hrBefore ?: JSONObject.NULL)
                    put("hrAfter", r.hrAfter ?: JSONObject.NULL)
                    put("rating", r.rating)
                    put("ts", r.ts)
                }
            )
        }
        sp.edit().putString(KEY_RECORDS, arr.toString()).apply()
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
            }.filter { it.id.isNotEmpty() && it.title.isNotEmpty() }
        }.getOrElse { null }
    }

    fun savePool(list: List<ActionItem>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("emoji", a.emoji)
                    put("title", a.title)
                    put("minutes", a.minutes)
                    put("category", a.category.name)
                    put("cue", a.cue)
                }
            )
        }
        sp.edit().putString(KEY_POOL, arr.toString()).apply()
    }

    fun clearRecords() {
        sp.edit().remove(KEY_RECORDS).apply()
    }

    private companion object {
        const val KEY_RECORDS = "records_v1"
        const val KEY_POOL = "pool_v1"
    }
}