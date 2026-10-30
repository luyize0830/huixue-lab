package com.example.blehr.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.ExperimentRecord

/** 首次启动时给用户一池"能马上做"的小行动，之后完全由用户自己增删 */
object DefaultPool {
    val items: List<ActionItem> = listOf(
        ActionItem("walk", "🚶", "下楼走一圈", 5, ActionCategory.MOVE, "绕楼走 300 米，不刷手机"),
        ActionItem("water", "💧", "接水 + 漱个口", 3, ActionCategory.FUEL, "慢慢喝完半杯，别一口闷"),
        ActionItem("breath", "🌬", "4-7-8 呼吸", 3, ActionCategory.CALM, "吸 4 秒、屏 7 秒、呼 8 秒，做 4 轮"),
        ActionItem("song", "🎧", "完整听完一首歌", 4, ActionCategory.PLAY, "闭眼听完，中途不切歌"),
        ActionItem("pet", "🐱", "撸猫 / 撸狗 / 抱枕", 3, ActionCategory.SOCIAL, "没有宠物就抱个玩偶，感受重量"),
        ActionItem("wash", "🚿", "洗把脸 + 热毛巾敷眼", 3, ActionCategory.REST, "水温偏凉一点，敷眼 60 秒"),
        ActionItem("voice", "📞", "给朋友发一条语音", 4, ActionCategory.SOCIAL, "只说一句：我今天有点累"),
        ActionItem("lie", "🛏", "平躺闭眼", 10, ActionCategory.REST, "不睡也行，就是不睁眼、不看屏幕"),
        ActionItem("far", "🌳", "看远处发呆", 5, ActionCategory.CALM, "看 20 米外的东西，数 3 个会动的")
    )
}

/**
 * 全局状态容器：用 Compose 的 SnapshotStateList 直接驱动界面刷新，
 * 不引入 ViewModel / DI 框架（这个规模用不上，也降低评审时的理解成本）。
 */
object Repo {

    private var store: Store? = null

    val records = mutableStateListOf<ExperimentRecord>()
    val pool = mutableStateListOf<ActionItem>()

    var ready by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (ready) return
        val s = Store(context)
        store = s

        records.clear()
        records.addAll(s.loadRecords().sortedBy { it.ts })

        val saved = s.loadPool()
        pool.clear()
        pool.addAll(saved ?: DefaultPool.items)
        if (saved == null) s.savePool(pool.toList())

        ready = true
    }

    fun addRecord(record: ExperimentRecord) {
        records.add(record)
        store?.saveRecords(records.toList())
    }

    fun clearRecords() {
        records.clear()
        store?.clearRecords()
    }

    fun addAction(item: ActionItem) {
        pool.add(item)
        store?.savePool(pool.toList())
    }

    fun removeAction(id: String) {
        pool.removeAll { it.id == id }
        store?.savePool(pool.toList())
    }

    fun findAction(id: String): ActionItem? = pool.firstOrNull { it.id == id }
}