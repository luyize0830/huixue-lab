package com.example.blehr.logic

import com.example.blehr.model.ActionItem
import com.example.blehr.model.EnergyBand

/**
 * 大模型层：把"用户随口一句"翻译成"3 个能点的行动"。
 *
 * 设计上的三条底线（也是答辩会被问到的）：
 * 1. 模型只负责"选"，不负责"编" —— 可选行动由本地清单提供 id，
 *    模型返回的 id 不在清单里就丢掉，所以它没法凭空发明一个"冥想 30 分钟"。
 * 2. 模型挂了不影响用 —— 任何异常都返回 null，调用方退回本地规则解析。
 * 3. 发给模型的话里不含心率、不含记录，只有用户刚打的这句话 + 行动目录。
 */
data class LlmSuggestion(
    val reply: String,
    val actionIds: List<String>,
    val minutes: Int?
)

object LlmPrompt {

    fun system(): String = """
        你是「回血实验室」里的助手。用户是大学生，正在硬撑。
        你的任务不是讲道理，是帮他挑 2 到 3 件 5 分钟内能做完的小事。

        规则：
        1. 只能从给定清单里挑，不许自己发明行动，也不许改行动名字。
        2. 深夜（23 点到 5 点）不要挑社交类（找人说话、发消息）。
        3. 在图书馆、教室时不要挑需要说话的。
        4. 用户说了时间预算，就只挑时长不超过它的。
        5. 不要给医疗建议、不要诊断、不要说"你应该"。
        6. 语气像熟悉的朋友，一到两句，不煽情。

        严格只输出 JSON，不要任何多余文字：
        {"reply":"一到两句话","actionIds":["行动id"],"minutes":10}
    """.trimIndent()

    /** 清单里只放 id/名字/时长/类别，不放 emoji 和长文案，省 token 也少干扰 */
    fun user(text: String, pool: List<ActionItem>, hour: Int, band: EnergyBand): String {
        val catalog = pool.joinToString("\n") { "${it.id}｜${it.title}｜${it.minutes}分钟｜${it.category.label}" }
        return """
            现在是 ${hour} 点，当前状态档位：${band.label}。
            用户刚说：「$text」

            可选清单（id｜名字｜时长｜类别）：
            $catalog
        """.trimIndent()
    }
}

object LlmReplyParser {

    /** 解析模型的输出；格式不对就返回 null，让调用方退回本地规则 */
    fun parse(raw: String?): LlmSuggestion? {
        if (raw.isNullOrBlank()) return null
        val obj = JsonLite.extractObject(raw) ?: return null
        val reply = JsonLite.readString(obj, "reply")?.trim().orEmpty()
        val ids = JsonLite.readStringArray(obj, "actionIds").map { it.trim() }.filter { it.isNotEmpty() }
        if (reply.isEmpty() || ids.isEmpty()) return null
        val minutes = JsonLite.readInt(obj, "minutes")?.takeIf { it in 1..120 }
        return LlmSuggestion(reply, ids.distinct(), minutes)
    }

    /** 只保留真实存在于池子里的 id，顺序按模型给的顺序 */
    fun pick(pool: List<ActionItem>, ids: List<String>, limit: Int = 3): List<ActionItem> {
        val byId = pool.associateBy { it.id }
        return ids.mapNotNull { byId[it] }.distinctBy { it.id }.take(limit)
    }
}