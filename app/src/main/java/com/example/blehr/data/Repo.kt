package com.example.blehr.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.ExperimentRecord

/**
 * 默认回血池：按"五分钟内能不能做完"挑的，都是宿舍/宿舍楼下/图书馆就能做的事。
 *
 * 设计原则（也是答辩要讲的）：
 * 1. 不推荐"看个电影""出去旅行"这种听着爽但做不到的事——推荐被拒绝一次，信任掉一格；
 * 2. 每个行动都写"cue"，告诉你第一步抬哪只手，降低启动成本；
 * 3. 覆盖 6 类回血方式，让算法能学到"你这个人"更吃哪一类，而不是只会推喝水。
 */
object DefaultPool {
    val items: List<ActionItem> = listOf(
        // —— 动一动（MOVE） ——
        ActionItem("walk", "🚶", "下楼走一圈", 5, ActionCategory.MOVE, "不用换衣服，穿拖鞋先下楼，走到楼门口就算完成"),
        ActionItem("stairs", "🪜", "爬两层楼梯", 3, ActionCategory.MOVE, "一层一层走，不用快，重点是把心率撑起来一点"),
        ActionItem("stretch", "🧘", "站起来伸个懒腰", 2, ActionCategory.MOVE, "双手举过头顶，往上够 10 秒，再左右各转一下腰"),
        ActionItem("sun", "☀️", "去窗边晒 2 分钟", 2, ActionCategory.MOVE, "拉开窗帘站窗边，眼睛看远处，顺便眨眨眼"),
        ActionItem("jump", "🦘", "原地跳 20 下", 1, ActionCategory.MOVE, "轻一点别吵到楼下，跳完深呼吸三次"),

        // —— 静一静（CALM） ——
        ActionItem("breath", "🌬️", "4-7-8 呼吸", 3, ActionCategory.CALM, "吸气 4 秒、憋 7 秒、呼气 8 秒，做 4 轮就行"),
        ActionItem("far", "🏔️", "看远处 2 分钟", 2, ActionCategory.CALM, "找窗外最远的一个点，盯着看，别想别的"),
        ActionItem("song", "🎧", "听完一首歌", 4, ActionCategory.CALM, "选一首你熟的，不刷手机，只听"),
        ActionItem("shoulder", "💆", "揉开肩膀那两个硬块", 3, ActionCategory.CALM, "用另一只手的拇指压住肩颈交界处，转圈揉 30 秒再换边"),
        ActionItem("cold", "🧊", "冷水洗把脸", 1, ActionCategory.CALM, "水温调到凉，洗到后颈，搓两下再抬头"),

        // —— 找个人（SOCIAL） ——
        ActionItem("voice", "📣", "给朋友发条语音", 4, ActionCategory.SOCIAL, "随便说 30 秒，吐槽今天也行，不用等人回"),
        ActionItem("roommate", "🗣️", "跟室友说两句闲话", 3, ActionCategory.SOCIAL, "问一句「吃了吗」「今晚干啥」，聊两句就够"),
        ActionItem("call", "📞", "给家里人打个电话", 5, ActionCategory.SOCIAL, "就说「我挺好的，随便问问」，不用报备学习"),
        ActionItem("share", "🤝", "把现在的状态发群里", 2, ActionCategory.SOCIAL, "发一句「学不动了有没有人一起下楼」，往往真有人应"),

        // —— 玩一下（PLAY） ——
        ActionItem("video", "📺", "看一个搞笑视频", 3, ActionCategory.PLAY, "只看一个，看完就关，不要点「下一个」"),
        ActionItem("game", "🎮", "玩一局小游戏", 5, ActionCategory.PLAY, "一局定胜负，输了也停，当成换脑子不是逃避"),
        ActionItem("pet", "🐱", "撸猫/逗楼下那只", 3, ActionCategory.PLAY, "揉两下头，看它打哈欠，你也会跟着松下来"),
        ActionItem("draw", "✏️", "随手画点没用的", 3, ActionCategory.PLAY, "不用好看，画桌上那支笔就行"),

        // —— 歇一下（REST） ——
        ActionItem("lie", "🛏️", "平躺闭眼 10 分钟", 10, ActionCategory.REST, "定个 10 分钟闹钟，手机放远一点，不许拿起来"),
        ActionItem("eyes", "👀", "热敷一下眼睛", 5, ActionCategory.REST, "双手搓热捂眼，或拿热毛巾，敷到不热为止"),
        ActionItem("nap", "😴", "趴桌上眯一会儿", 8, ActionCategory.REST, "定 8 分钟，别超过 20 分钟，不然醒来更懵"),
        ActionItem("wash", "🧼", "洗把脸 + 换件衣服", 4, ActionCategory.REST, "衣服一换，身体会收到「要开始了」的信号"),

        // —— 吃点喝点（FUEL） ——
        ActionItem("water", "💧", "接一杯水慢慢喝", 1, ActionCategory.FUEL, "接满一杯，站在饮水机边喝完再回去"),
        ActionItem("fruit", "🍊", "吃个水果", 3, ActionCategory.FUEL, "剥的时候慢一点，别边吃边看屏幕"),
        ActionItem("tea", "🍵", "泡杯茶/热饮", 4, ActionCategory.FUEL, "等水开的那几分钟，就是给自己按了个暂停"),
        ActionItem("nuts", "🥜", "吃点坚果或小零食", 2, ActionCategory.FUEL, "抓一小把，吃完把手擦干净，别一直捏着")
    )
}

/**
 * 全 App 唯一的可变数据源。
 *
 * 用 mutableStateListOf 是为了让 Compose 直接观察增删；持久化走 SharedPreferences(JSON)，
 * 数据量小（几十条记录），够用且零依赖——这是有意为之的技术选择，不是偷懒。
 */
object Repo {
    val records = mutableStateListOf<ExperimentRecord>()
    val pool = mutableStateListOf<ActionItem>()

    var ready by mutableStateOf(false)
        private set

    private var store: Store? = null

    fun init(context: Context) {
        if (ready) return
        val s = Store(context.applicationContext)
        store = s

        val savedPool = s.loadPool()
        if (savedPool == null) {
            pool.addAll(DefaultPool.items)
            s.savePool(pool)
        } else {
            pool.addAll(savedPool)
        }

        records.addAll(s.loadRecords())
        ready = true
    }

    fun addRecord(r: ExperimentRecord) {
        records.add(0, r)
        store?.saveRecords(records)
    }

    fun clearRecords() {
        records.clear()
        store?.saveRecords(records)
    }

    fun addAction(a: ActionItem) {
        if (pool.any { it.id == a.id }) return
        pool.add(a)
        store?.savePool(pool)
    }

    fun removeAction(id: String) {
        pool.removeAll { it.id == id }
        store?.savePool(pool)
    }

    fun findAction(id: String): ActionItem? = pool.firstOrNull { it.id == id }

    fun resetPool() {
        pool.clear()
        pool.addAll(DefaultPool.items)
        store?.savePool(pool)
    }
}