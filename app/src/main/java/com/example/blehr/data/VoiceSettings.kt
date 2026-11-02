package com.example.blehr.data

import android.content.Context

/**
 * 语音输出开关。
 *
 * 默认是关的：一个人第一次打开 App，手机突然开口说话会吓一跳。
 * 打开之后，她回你的每一条都会念出来——用的还是系统自带的 TTS，
 * 所以我们不打包音色、不要录音权限，也不上传任何音频。
 */
object VoiceSettings {

    private const val PREFS = "huixue_voice"
    private const val KEY_ENABLED = "enabled_v1"

    fun enabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
    }
}