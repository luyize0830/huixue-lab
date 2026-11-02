package com.example.blehr.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * 本机账号。
 *
 * 说清楚：这不是"注册一个云账号"，也没有服务器。它只负责两件事——
 * 让界面知道你怎么称呼自己，以及让记录有个归属。
 * 比赛要求"数据诚实"，所以界面上会明写"本地账号，数据不出手机"，
 * 等真有账号体系的那天再替换掉这一层（接口已经收在这里）。
 */
data class Profile(
    val nickname: String = "",
    val school: String = "",
    val major: String = "",
    val createdAt: Long = 0L
) {
    val signedIn: Boolean get() = nickname.isNotBlank()
    val initial: String get() = nickname.trim().take(1).ifBlank { "回" }

    /** 一句副标题：有学校就显示学校，没有就退回到身份说明 */
    val subtitle: String
        get() = listOf(school, major).filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { if (signedIn) "本地账号 · 数据只在本机" else "还没登录 · 记录仍然存在本机" }
}

object ProfileStore {

    private const val PREFS = "huixue_profile"

    fun load(ctx: Context): Profile {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("profile_v1", null) ?: return Profile()
        return runCatching {
            val o = JSONObject(raw)
            Profile(
                nickname = o.optString("nickname", ""),
                school = o.optString("school", ""),
                major = o.optString("major", ""),
                createdAt = o.optLong("createdAt", 0L)
            )
        }.getOrDefault(Profile())
    }

    fun save(ctx: Context, p: Profile) {
        val now = if (p.createdAt > 0L) p.createdAt else System.currentTimeMillis()
        val o = JSONObject()
            .put("nickname", p.nickname.trim())
            .put("school", p.school.trim())
            .put("major", p.major.trim())
            .put("createdAt", now)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("profile_v1", o.toString()).apply()
    }

    fun signOut(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("profile_v1").apply()
    }

    /**
     * 认脸页只该出现一次。
     * 用户点了「先不填，随便逛逛」，这个决定要记住——否则每次冷启动都拦一次，
     * 等于用一次弹窗换一次卸载理由。
     */
    fun seen(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("seen_v1", false)

    fun markSeen(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("seen_v1", true).apply()
    }
}

/**
 * Compose 侧持有者：登录后立刻要能在界面上看到，所以用一个可变状态兜住。
 * 真正的持久化仍然落在 SharedPreferences，进程重启不丢。
 */
object ProfileRepo {

    var current: Profile by mutableStateOf(Profile())
        private set

    fun init(ctx: Context) {
        current = ProfileStore.load(ctx)
    }

    fun save(ctx: Context, nickname: String, school: String, major: String) {
        val p = current.copy(nickname = nickname, school = school, major = major)
        ProfileStore.save(ctx, p)
        current = ProfileStore.load(ctx)
    }

    fun signOut(ctx: Context) {
        ProfileStore.signOut(ctx)
        current = Profile()
    }
}