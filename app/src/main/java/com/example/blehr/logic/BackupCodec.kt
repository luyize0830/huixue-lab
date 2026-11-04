package com.example.blehr.logic

import org.json.JSONObject

data class BackupData(val lab: LabData, val preferences: Map<String, Map<String, Any>>, val createdAt: Long)

object BackupCodec {
    // Export only user-owned content and choices; never endpoint credentials, device identifiers or pending nudges.
    val allowed = mapOf(
        "huixue_profile" to mapOf("profile_v1" to "string", "seen_v1" to "boolean"),
        "huixue_chat" to mapOf("turns_v1" to "string", "note_v1" to "string", "last_talk_day_v1" to "string"),
        "huixue_persona" to mapOf("preset_v1" to "string", "custom_v1" to "string"),
        "huixue_voice" to mapOf("enabled_v1" to "boolean"),
        "huixue_music" to mapOf("bound_v1" to "string", "suggest_enabled_v1" to "boolean"),
        "huixue_nudge" to mapOf("enabled" to "boolean")
    )

    fun encode(b: BackupData): String {
        val prefs = JSONObject()
        b.preferences.forEach { (name, values) -> prefs.put(name, JSONObject(values)) }
        return JSONObject().put("format", "huixue-lab-backup").put("version", 1)
            .put("platform", "android").put("createdAt", b.createdAt)
            .put("lab", LabCodec.encode(b.lab)).put("preferences", prefs).toString(2)
    }

    fun decode(text: String): BackupData {
        require(text.toByteArray(Charsets.UTF_8).size <= LabCodec.MAX_BYTES) { "备份不能超过 8 MB" }
        val o = JSONObject(text.removePrefix("\uFEFF"))
        require(o.optString("format") == "huixue-lab-backup" && o.optString("platform") == "android") {
            "请选择安卓版回血实验室导出的备份文件" }
        require(o.get("version") == 1) { "备份版本不支持，请使用匹配版本的软件" }
        val at = o.get("createdAt")
        require((at is Long || at is Int) && (at as Number).toLong() >= 0) { "备份时间不正确" }
        val prefs = o.getJSONObject("preferences")
        val result = mutableMapOf<String, Map<String, Any>>()
        prefs.keys().forEach { name ->
            val keys = allowed[name] ?: error("备份包含不支持的设置")
            val values = prefs.getJSONObject(name)
            result[name] = values.keys().asSequence().associateWith { key ->
                val type = keys[key] ?: error("备份包含不支持的设置")
                val value = values.get(key)
                require(if (type == "string") value is String && value.length <= 200_000 else value is Boolean) {
                    "备份设置的内容不正确" }
                if (name == "huixue_profile" && key == "profile_v1") JSONObject(value as String)
                value
            }
        }
        require(result.keys == allowed.keys) { "备份设置不完整" }
        return BackupData(LabCodec.decode(o.getJSONObject("lab")), result, (at as Number).toLong())
    }
}
