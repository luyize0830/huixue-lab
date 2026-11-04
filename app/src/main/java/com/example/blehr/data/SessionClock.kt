package com.example.blehr.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.example.blehr.logic.ActionSession

object SessionClock {
    fun boot(ctx: Context): Int = runCatching {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrDefault(-1)
    fun remaining(ctx: Context, s: ActionSession): Int =
        s.remaining(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(ctx))
}
