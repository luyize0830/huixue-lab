package com.example.blehr.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 手机行为信号的 Compose 侧持有者。
 * 由 MainActivity 在 onResume 时刷新，App 内部再每 30 秒轮询一次。
 */
object UsageRepo {

    var snapshot: UsageSnapshot by mutableStateOf(UsageSnapshot(false, 0, 0))
        private set

    fun refresh(ctx: Context) {
        snapshot = ScreenUsage.snapshot(ctx)
    }
}