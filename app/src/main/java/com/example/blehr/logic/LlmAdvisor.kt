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

    /** 解析模型的输出；格式不对就退回"把整段话当回复"，让调用方仍有话可说 */
    fun parse(raw: String?): LlmSuggestion? {
        if (raw.isNullOrBlank()) return null
        val text = JsonLite.stripFences(raw).trim()
        val obj = JsonLite.extractObject(text)
        if (obj != null) {
            val reply = JsonLite.readString(obj, "reply")?.trim().orEmpty()
            // 是 JSON 却没有 reply：这是格式错，别硬猜
            if (reply.isEmpty()) return null
            // 闲聊时模型可能返回 []、也可能干脆不给这个字段，都当成"这次没有行动"
            val ids = JsonLite.readStringArray(obj, "actionIds")
                .map { it.trim() }.filter { it.isNotEmpty() }
            val minutes = JsonLite.readInt(obj, "minutes")?.takeIf { it in 1..120 }
            return LlmSuggestion(dropPoolEcho(reply), ids.distinct(), minutes)
        }
        // 模型没按 JSON 回（真实模型经常这样）：这段就是她想说的那句话。
        // 宁可显示一句人话，也别让用户看到"我没看懂"——那是我的问题，不是他的。
        val plain = dropPoolEcho(
            text.trim().trim('{', '}').replace("\"reply\"", "").replace(":", " ").trim()
        )
        return plain.takeIf { it.isNotEmpty() }?.let { LlmSuggestion(it.take(400), emptyList(), null) }
    }

    /**
     * 模型有时把行动清单原样抄进回复里（"cold | 冷水洗脸" 这种）。
     * 那是给程序看的，不该念给用户听，所以把这种行整行去掉。
     */
    private fun dropPoolEcho(reply: String): String {
        val lines = reply.lines()
        val kept = lines.filterNot { it.trim().matches(Regex("^[a-z][a-z0-9_]*\\s*\\|.*")) }
        val out = kept.joinToString("\n").trim()
        // 全被过滤掉（说明模型只回了清单）就别自作聪明，原样返回
        return out.ifBlank { reply.trim() }
    }

    /** 只保留真实存在于池子里的 id，顺序按模型给的顺序 */
    fun pick(pool: List<ActionItem>, ids: List<String>, limit: Int = 3): List<ActionItem> {
        val byId = pool.associateBy { it.id }
        return ids.mapNotNull { byId[it] }.distinctBy { it.id }.take(limit)
    }
}