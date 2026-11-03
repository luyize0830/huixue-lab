package com.example.blehr.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 「绑定我的音乐 App」。
 *
 * 这里必须说清楚一件事：第三方音乐 App **没有**开放"播放指定歌曲"的公共接口，
 * QQ音乐/网易云的 URL scheme 大多未公开、随版本变。
 * 所以我们不做逆向、不做自动化播放，只做三件老实的事：
 *   1. 记住你常用哪个 App；
 *   2. 把「轻音乐」这个关键词准备好；
 *   3. 一键把你送到那个 App 里（能深链就深链，不能就打开 App 并复制关键词）。
 *
 * 真正能"按一下就出声"的，是我们自己内置的三段音景（见 player/AmbientPlayer.kt）。
 */
data class MusicApp(val label: String, val pkg: String)

object MusicBinding {

    private const val PREFS = "huixue_music"
    private const val KEY_BOUND = "bound_v1"
    private const val KEY_ENABLED = "suggest_enabled_v1"
    private const val KEY_LAST = "last_suggest_v1"

    /** 只列真的能搜到音乐的：不做全市场扫描，避免越权查应用列表 */
    val candidates = listOf(
        MusicApp("QQ音乐", "com.tencent.qqmusic"),
        MusicApp("网易云音乐", "com.netease.cloudmusic"),
        MusicApp("酷狗音乐", "com.kugou.android"),
        MusicApp("酷我音乐", "cn.kuwo.player")
    )

    /** 推荐时用的搜索词 */
    const val KEYWORD = "轻音乐"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 本机装了哪些（只判断候选里的四个，其余不管） */
    fun installed(ctx: Context): List<MusicApp> = candidates.filter { app ->
        runCatching {
            ctx.packageManager.getLaunchIntentForPackage(app.pkg) != null ||
                ctx.packageManager.getPackageInfo(app.pkg, 0) != null
        }.getOrDefault(false)
    }

    fun bound(ctx: Context): MusicApp? {
        val pkg = prefs(ctx).getString(KEY_BOUND, null) ?: return null
        return candidates.firstOrNull { it.pkg == pkg }
    }

    fun setBound(ctx: Context, app: MusicApp?) {
        prefs(ctx).edit().apply {
            if (app == null) remove(KEY_BOUND) else putString(KEY_BOUND, app.pkg)
        }.apply()
    }

    /** 小夜能不能自己推音乐（默认开） */
    fun suggestionsEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, true)

    fun setSuggestionsEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun lastSuggestedAt(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST, 0L)

    fun markSuggested(ctx: Context, at: Long = System.currentTimeMillis()) {
        prefs(ctx).edit().putLong(KEY_LAST, at).apply()
    }

    /**
     * 打开第三方 App。能深链到搜索页就深链，否则退化成"打开 App"。
     * @return true 表示确实启动了某个界面
     */
    fun openApp(ctx: Context, app: MusicApp): Boolean {
        // 只有 QQ音乐 有相对稳定的搜索深链；失败会走下面的兜底
        if (app.pkg == "com.tencent.qqmusic") {
            val deep = runCatching {
                Intent(Intent.ACTION_VIEW, Uri.parse("qqmusic://qq.com/ui/search?p=${Uri.encode(KEYWORD)}"))
                    .setPackage(app.pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.getOrNull()
            if (deep != null && deep.resolveActivity(ctx.packageManager) != null) {
                runCatching { ctx.startActivity(deep) }.onSuccess { return true }
            }
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(launch) }.isSuccess
    }

    /** 把关键词放进剪贴板：跳过去之后粘一下就能搜 */
    fun copyKeyword(ctx: Context) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("回血实验室", KEYWORD))
    }
}