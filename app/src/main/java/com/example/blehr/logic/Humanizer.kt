package com.example.blehr.logic

/**
 * 把模型的一大段话压回"人在发微信"的样子。
 *
 * 为什么要有这一层：提示词再怎么写，模型仍然倾向于把话说完整——
 * 一句话讲清来龙去脉、末尾加个总结。真人不这样。真人一条消息十几个字，
 * 想到什么补一条，句子可以不完整，句尾常带语气词。
 *
 * 所以这里做三件事：
 * 1. 拆：按 || 拆成多条，拆不开就按句号问号感叹号拆，再按长度并；
 * 2. 洗：删掉"建议你""总之""希望能帮到你"这类一眼是机器在说话的词；
 * 3. 收：句尾的句号去掉（微信里没人发句号），每条压到 30 字以内。
 *
 * 纯逻辑，不依赖 Android，可单测。
 */
object Humanizer {

    /** 一次最多几条消息。真人连发也极少超过三条。 */
    const val MAX_BUBBLES = 3

    /** 单条消息的软上限，超过就在逗号处截。 */
    const val MAX_CHARS = 30

    /**
     * 一眼是机器/客服在说的词。整句含这些词就把那句丢掉，
     * 而不是逐词替换——「建议你先休息」删掉"建议你"会变成半句病句。
     */
    private val BOT_TELLS = listOf(
        "建议你", "建议您", "你可以考虑", "你可以试试", "希望对你", "希望能帮到你",
        "作为 AI", "作为一个 AI", "总之", "综上所述", "总结一下", "首先", "其次",
        "如果你愿意", "我很乐意", "有什么我可以帮你", "随时告诉我"
    )

    private val SENTENCE_END = charArrayOf('。', '！', '？', '!', '?', '…', '；', ';')

    /**
     * @return 一到三条消息；返回空列表表示"她这次不说话"。
     */
    fun split(reply: String?): List<String> {
        if (reply.isNullOrBlank()) return emptyList()

        // 1) 模型按约定用 || 分隔；兼容全角与换行
        var chunks = reply
            .replace("｜｜", "||")
            .replace("\r\n", "\n")
            .split("||", "\n")
            .map { clean(it) }
            .filter { it.isNotBlank() }

        // 2) 没分隔符、且太长 —— 按句子重新切
        if (chunks.size == 1 && chunks[0].length > MAX_CHARS) {
            chunks = bySentences(chunks[0])
        }

        // 3) 压到 MAX_BUBBLES 条：多出来的并进最后一条
        if (chunks.size > MAX_BUBBLES) {
            val head = chunks.take(MAX_BUBBLES - 1)
            val tail = chunks.drop(MAX_BUBBLES - 1).joinToString("")
            chunks = head + clip(tail)
        }

        // 4) 每条再限一下长度，并去掉句尾句号
        return chunks
            .map { clip(it) }
            .map { it.trimEnd('。', '，', ',', '、', ' ') }
            .filter { it.isNotBlank() }
    }

    /** 删掉客服腔的分句；整段都被删空时返回空串（等于这次不说话）。 */
    fun clean(text: String): String {
        val parts = bySentencesRaw(text)
        val kept = parts.filterNot { p -> BOT_TELLS.any { p.contains(it) } }
        return kept.joinToString("").trim()
    }

    /** 按句末标点切句，保留标点。 */
    private fun bySentencesRaw(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (c in text) {
            sb.append(c)
            if (c in SENTENCE_END) {
                out.add(sb.toString())
                sb.clear()
            }
        }
        if (sb.isNotBlank()) out.add(sb.toString())
        return out
    }

    /** 切成若干条，每条尽量不超过 MAX_CHARS。 */
    private fun bySentences(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (sentence in bySentencesRaw(text)) {
            if (sb.isNotEmpty() && (sb.length + sentence.length) > MAX_CHARS) {
                out.add(sb.toString())
                sb.clear()
            }
            sb.append(sentence)
        }
        if (sb.isNotBlank()) out.add(sb.toString())
        return out.ifEmpty { listOf(text) }
    }

    /** 超长时在最后一个逗号处截断；实在没逗号就硬截。 */
    private fun clip(text: String): String {
        if (text.length <= MAX_CHARS) return text
        val cut = text.lastIndexOfAny(charArrayOf('，', ',', '、', ' '), MAX_CHARS)
        return if (cut > 6) text.substring(0, cut) else text.substring(0, MAX_CHARS)
    }
}