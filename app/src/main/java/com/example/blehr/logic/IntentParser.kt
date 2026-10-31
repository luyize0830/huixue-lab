package com.example.blehr.logic

import com.example.blehr.model.ActionCategory
import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ActionItem
import com.example.blehr.model.ExperimentRecord

/** 用户这句话里的情绪倾向 */
enum class Mood(val label: String) {
    TIRED("累"),
    ANXIOUS("烦"),
    NUMB("空"),
    OK("还行")
}

/** 用户当下在哪儿——决定推荐哪些行动（图书馆不能出声、宿舍可以躺） */
enum class Scene(val label: String) {
    DORM("在宿舍"),
    LIBRARY("在图书馆"),
    CLASSROOM("在教室"),
    COMMUTE("在路上"),
    UNKNOWN("不确定在哪")
}

data class ParsedIntent(
    val mood: Mood,
    val scene: Scene,
    val minutesBudget: Int?,
    val lateNight: Boolean,
    val matched: List<String>
)

/**
 * 本地意图理解：把"我有点撑不住了，图书馆还得再看一小时"这种口语，
 * 拆成 (情绪, 场景, 时间预算) 三个槽位。
 *
 * 为什么先做规则而不是直接接大模型（重要，文档里要写诚实）：
 * - 这个 App 的核心承诺之一是"离线可用、状态数据不出手机"；
 * - 三个槽位的词表很窄，规则准确率够用，而且延迟是 0、零成本、可单测；
 * - 架构上留了 adapter 位：以后接 LLM 只换这一层，下游 ChatAdvisor 不动。
 * 所以我们在文档里写的是"当前为本地规则解析"，不吹成"大模型对话"。
 */
object IntentParser {

    private val tiredWords = listOf("累", "困", "撑不住", "没力气", "疲惫", "熬不住", "睡不够", "打不起精神")
    private val anxiousWords = listOf("烦", "焦虑", "慌", "压力", "紧张", "怕", "赶不上", "来不及", "慌得")
    private val numbWords = listOf("麻木", "空", "没感觉", "无聊", "提不起", "摆烂", "没劲", "学不进去")

    // 中文数字也得认：用户说的是"还有十分钟"，不是"还有10分钟"
    private val timeRegex = Regex("""([0-9]+(?:\.[0-9]+)?|[一二三四五六七八九十两半]+)\s*个?\s*(小时|分钟|min|h|分)""")
    private val cnDigits = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )

    private fun cnToNum(s: String): Double? {
        if (s == "半") return 0.5
        if (s.contains('半')) return null
        val tenIdx = s.indexOf('十')
        return when {
            tenIdx < 0 -> s.singleOrNull()?.let { cnDigits[it]?.toDouble() }
            tenIdx == 0 -> 10.0 + (s.getOrNull(1)?.let { cnDigits[it] } ?: 0)
            else -> {
                val tens = cnDigits[s[tenIdx - 1]] ?: return null
                val ones = s.getOrNull(tenIdx + 1)?.let { cnDigits[it] } ?: 0
                tens * 10.0 + ones
            }
        }
    }

    private fun numOf(raw: String): Double? =
        raw.toDoubleOrNull() ?: cnToNum(raw)

    private fun firstHit(text: String, words: List<String>): String? =
        words.firstOrNull { text.contains(it) }

    fun parse(text: String, hour: Int): ParsedIntent {
        val t = text.trim()
        val matched = mutableListOf<String>()

        val moodHit = firstHit(t, numbWords)?.let { Mood.NUMB to it }
            ?: firstHit(t, anxiousWords)?.let { Mood.ANXIOUS to it }
            ?: firstHit(t, tiredWords)?.let { Mood.TIRED to it }
        val mood = moodHit?.first ?: Mood.OK
        moodHit?.second?.let { matched += it }

        val scene = when {
            listOf("图书馆", "自习室", "书库", "阅览室").any { t.contains(it) } -> Scene.LIBRARY.also { matched += "图书馆" }
            listOf("宿舍", "寝室", "床上", "被窝", "家里", "屋里").any { t.contains(it) } -> Scene.DORM.also { matched += "宿舍" }
            listOf("教室", "上课", "班里", "课堂").any { t.contains(it) } -> Scene.CLASSROOM.also { matched += "教室" }
            listOf("地铁", "公交", "路上", "通勤", "食堂", "走路").any { t.contains(it) } -> Scene.COMMUTE.also { matched += "路上" }
            else -> Scene.UNKNOWN
        }

        var budget: Int? = null
        timeRegex.find(t)?.let { m ->
            val num = numOf(m.groupValues[1])
            if (num != null) {
                val unit = m.groupValues[2]
                budget = if (unit == "小时" || unit == "h") (num * 60).toInt() else num.toInt()
                matched += m.value
            }
        }
        if (budget == null && listOf("一会儿", "马上", "几分钟", "一小会", "很快").any { t.contains(it) }) {
            budget = 5
            matched += "一会儿"
        }

        return ParsedIntent(
            mood = mood,
            scene = scene,
            minutesBudget = budget,
            lateNight = hour >= 23 || hour < 5,
            matched = matched
        )
    }
}

data class ChatReply(
    val text: String,
    val recommendations: List<Recommendation>
)

/**
 * 把"意图 + 当前状态 + 历史记录"揉成一段人话回复，并给出 3 个可执行选项。
 *
 * 注意这里刻意不做的事：不说"你应该休息""你的焦虑水平偏高"。
 * 这个 App 是实验助手不是医生，只给"要不要试一下这个"级别的建议，决定权留给用户。
 */
object ChatAdvisor {

    fun reply(
        intent: ParsedIntent,
        band: EnergyBand,
        pool: List<ActionItem>,
        records: List<ExperimentRecord>,
        limit: Int = 3
    ): ChatReply {
        val budget = intent.minutesBudget
        val constraints = RecommendationConstraints(
            maxMinutes = budget,
            nightMode = intent.lateNight
        )

        var recs = Recommender.recommend(band, pool, records, limit, constraints)

        // 场景过滤：图书馆/教室不能出声、不能躺，把这些行动排到后面
        if (recs.isNotEmpty()) {
            val blocked = when (intent.scene) {
                Scene.LIBRARY, Scene.CLASSROOM -> setOf(ActionCategory.SOCIAL, ActionCategory.REST)
                Scene.COMMUTE -> setOf(ActionCategory.REST)
                else -> emptySet()
            }
            val preferred = recs.filter { it.action.category !in blocked }
            if (preferred.isNotEmpty()) recs = preferred
        }

        val sb = StringBuilder()

        // 第一句：承认处境，而不是先给方案
        val where = when (intent.scene) {
            Scene.UNKNOWN -> ""
            else -> intent.scene.label + "，"
        }
        val feel = when (intent.mood) {
            Mood.TIRED -> "现在这个累是身体在提醒你，不是意志力问题。"
            Mood.ANXIOUS -> "烦的时候硬坐着，效率一般都不高。"
            Mood.NUMB -> "提不起劲的时候，做点小的、不用动脑的事，比「再坚持一下」有用。"
            Mood.OK -> "状态还行的话，也可以主动给自己放个小假。"
        }
        sb.append(where).append(feel)

        // 第二句：交代我做了什么筛选（体现"可解释"，而不是黑箱）
        val filterDesc = buildList {
            budget?.let { add("只要 ${it} 分钟以内的") }
            if (intent.lateNight) add("这个点只给你能收尾的")
            when (intent.scene) {
                Scene.LIBRARY, Scene.CLASSROOM -> add("排掉了要说话和要躺下的")
                Scene.COMMUTE -> add("排掉了要躺下的")
                else -> {}
            }
        }
        if (filterDesc.isNotEmpty()) {
            sb.append("按你说的条件（").append(filterDesc.joinToString("、")).append("）挑了几个：\n")
        } else {
            sb.append("给你三个，随便挑一个，不想挑就选第一个：\n")
        }

        if (recs.isEmpty()) {
            sb.append("按这个条件没剩下合适的了，把时间放宽一点我再试试。")
        } else {
            // 具体行动交给下面的卡片展示，正文里不重复列一遍（避免 0./1./2. 与卡片序号打架）
            sb.append("下面 ${recs.size} 个，挑一个点「开始」就行。做完回来点一下，我记下这次到底有没有用。")
        }

        return ChatReply(sb.toString(), recs)
    }
}