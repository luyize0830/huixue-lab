package com.example.blehr.data

import android.content.Context
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

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun presetId(ctx: Context): String =
        prefs(ctx).getString(KEY_PRESET, PersonaPreset.DEFAULT_ID) ?: PersonaPreset.DEFAULT_ID

    fun setPresetId(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_PRESET, id).apply()
    }

    fun custom(ctx: Context): String = prefs(ctx).getString(KEY_CUSTOM, "").orEmpty()

    fun setCustom(ctx: Context, text: String) {
        prefs(ctx).edit().putString(KEY_CUSTOM, text).apply()
    }

    /** 拼进 system prompt 的那段话 */
    fun style(ctx: Context): String = PersonaPreset.styleOf(presetId(ctx), custom(ctx))

    fun reset(ctx: Context) {
        prefs(ctx).edit()
            .putString(KEY_PRESET, PersonaPreset.DEFAULT_ID)
            .putString(KEY_CUSTOM, "")
            .apply()
    }
}