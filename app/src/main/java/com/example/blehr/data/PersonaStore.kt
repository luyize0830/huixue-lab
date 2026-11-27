package com.example.blehr.data

import android.content.Context
import android.content.SharedPreferences
import com.example.blehr.logic.PersonaPreset

/**
 * 小夜的"说话方式"存在本机：一个预设 id + 一段用户自己写的文字。
 *
 * 不联网、不开源到任何地方。改了立刻对下一次对话生效（每次发消息前现读）。
 */
object PersonaStore {

    private const val PREFS = "huixue_persona"
    private const val KEY_PRESET = "preset_v1"
    private const val KEY_CUSTOM = "custom_v1"
    private const val KEY_UNDO_PRESET = "undo_preset_v1"
    private const val KEY_UNDO_CUSTOM = "undo_custom_v1"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun presetId(ctx: Context): String =
        prefs(ctx).getString(KEY_PRESET, PersonaPreset.DEFAULT_ID) ?: PersonaPreset.DEFAULT_ID

    @Synchronized
    fun setPresetId(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_PRESET, id).apply()
    }

    fun custom(ctx: Context): String = prefs(ctx).getString(KEY_CUSTOM, "").orEmpty()

    @Synchronized
    fun setCustom(ctx: Context, text: String) {
        prefs(ctx).edit().putString(KEY_CUSTOM, text).apply()
    }

    /** 拼进 system prompt 的那段话 */
    @Synchronized
    fun style(ctx: Context): String {
        val current = prefs(ctx).all
        return PersonaPreset.styleOf(current[KEY_PRESET] as? String, current[KEY_CUSTOM] as? String)
    }

    @Synchronized
    fun save(ctx: Context, presetId: String, custom: String) {
        val preferences = prefs(ctx)
        val before = preferences.all
        val previousPreset = before[KEY_PRESET] as? String ?: PersonaPreset.DEFAULT_ID
        val previousCustom = before[KEY_CUSTOM] as? String ?: ""
        if (previousPreset == presetId && previousCustom == custom) return

        val editor = preferences.edit()
            .putString(KEY_PRESET, presetId)
            .putString(KEY_CUSTOM, custom)
            .putString(KEY_UNDO_PRESET, previousPreset)
            .putString(KEY_UNDO_CUSTOM, previousCustom)
        commit(preferences, before, editor)
    }

    fun reset(ctx: Context) {
        save(ctx, PersonaPreset.DEFAULT_ID, "")
    }

    @Synchronized
    fun hasUndo(ctx: Context): Boolean {
        val current = prefs(ctx).all
        return current[KEY_UNDO_PRESET] is String && current[KEY_UNDO_CUSTOM] is String
    }

    @Synchronized
    fun undo(ctx: Context) {
        val preferences = prefs(ctx)
        val before = preferences.all
        val presetId = checkNotNull(before[KEY_UNDO_PRESET] as? String) { "没有可撤销的人设设置" }
        val custom = checkNotNull(before[KEY_UNDO_CUSTOM] as? String) { "没有可撤销的人设设置" }
        val editor = preferences.edit()
            .putString(KEY_PRESET, presetId)
            .putString(KEY_CUSTOM, custom)
            .remove(KEY_UNDO_PRESET)
            .remove(KEY_UNDO_CUSTOM)
        commit(preferences, before, editor)
    }

    private fun commit(
        preferences: SharedPreferences,
        before: Map<String, *>,
        editor: SharedPreferences.Editor
    ) {
        if (editor.commit()) return

        // SharedPreferences updates its in-memory values even when the disk write fails.
        val rollback = preferences.edit()
        for (key in listOf(KEY_PRESET, KEY_CUSTOM, KEY_UNDO_PRESET, KEY_UNDO_CUSTOM)) {
            rollback.putString(key, before[key] as? String)
        }
        rollback.commit()
        error("人设设置保存失败")
    }
}
