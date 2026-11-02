package com.example.blehr.logic

import com.example.blehr.model.ActionItem

/**
 * 大模型层：把"用户随口一句"翻译成"一两句话 + 最多三个能点的行动"。
 *
 * 三条底线（答辩会被问）：
 * 1. 模型只负责"挑"，不负责"编" —— 行动由本地清单提供 id，模型返回的 id 不在清单里就丢掉，
 *    所以它没法凭空发明一个"冥想 30 分钟"。
 * 2. 模型挂了不影响用 —— 任何异常都返回 null，调用方退回本地规则。
 * 3. 发出去的内容不含心率、不含实验记录，只有这句话、最近几轮对话和动作目录。
 *
 * v0.11 起 actionIds 允许为空：她可以只是聊两句，不必每句都塞三个按钮。
 */
data class LlmSuggestion(
    val reply: String,
    val actionIds: List<String>,
    val minutes: Int?
)

object LlmReplyParser {

    /** 解析模型的输出；格式不对就返回 null，让调用方退回本地规则 */
    fun parse(raw: String?): LlmSuggestion? {
        if (raw.isNullOrBlank()) return null
        val obj = JsonLite.extractObject(raw) ?: return null
        val reply = JsonLite.readString(obj, "reply")?.trim().orEmpty()
        if (reply.isEmpty()) return null
        // 闲聊时模型可能返回 []、也可能干脆不给这个字段，都当成"这次没有行动"
        val ids = JsonLite.readStringArray(obj, "actionIds").map { it.trim() }.filter { it.isNotEmpty() }
        val minutes = JsonLite.readInt(obj, "minutes")?.takeIf { it in 1..120 }
        return LlmSuggestion(reply, ids.distinct(), minutes)
    }

    /** 只保留真实存在于池子里的 id，顺序按模型给的顺序 */
    fun pick(pool: List<ActionItem>, ids: List<String>, limit: Int = 3): List<ActionItem> {
        val byId = pool.associateBy { it.id }
        return ids.mapNotNull { byId[it] }.distinctBy { it.id }.take(limit)
    }
}