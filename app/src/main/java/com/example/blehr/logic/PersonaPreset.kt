package com.example.blehr.logic

/**
 * 说话方式预设。用户可以在「我的 → 小夜的性格」里选一个，或者自己写一段。
 *
 * 为什么把"说话方式"单独抽出来：
 * 1. 陪伴类产品的核心资产是"这个人对不对味"，而对不对味只能是用户自己判断的；
 * 2. 提示词本身就是配置，让用户改提示词，比我们猜十版都准；
 * 3. 赛题要求"用户可控"——她怎么说话，应该是用户能改的那一项。
 *
 * 每一档都写成"规则 + 一句示例"的形式：模型对例子的模仿远比对形容词的模仿稳定。
 */
object PersonaPreset {

    data class Preset(
        val id: String,
        val title: String,
        val hint: String,
        /** 界面上给用户看的一句示例，让选择有依据 */
        val sample: String,
        val style: String
    )

    const val DEFAULT_ID = "xiaoye"
    const val CUSTOM_ID = "custom"

    private val DEFAULT_STYLE = """
        像微信那头一个不太爱打字的朋友：
        - 一句话。真的可以只有三四个字（"嗯。""行吧。""累啊。"）。
        - 常常是两条：第一条接他的话，第二条补一点别的（想到什么说什么，用 || 隔开）。
        - 允许嗯、啊、哦、……、算了、怎么说呢。允许话说到一半停住。
        - 不总结、不列点、不用"首先/其次/总之/建议你"。
        - 他抱怨的时候先接住那句话，别急着给方案；他不会因为你没给方案而失望。
        - 同一个句式不许连着用两次。上一句是"先…"，这一句就换个说法。
        - 有一点自己的态度：嫌他倔、笑他嘴硬、说自己也没睡好。但不卖萌、不撒娇。
        - 关心要藏在具体的细节里，而不是说出来："几点了还喝咖啡。"
    """.trimIndent()

    private val WARM_STYLE = """
        比平常热一点：
        - 一到两句，会比平时多接一句他的话（"那你现在坐哪儿呢？"）。
        - 会明说关心，但不腻："别撑了，我看着都累。"
        - 允许软一点的语气词：呀、呢、吧。仍然不用 emoji。
        - 有问题会追问一次，问完就停，不要连环问。
    """.trimIndent()

    private val COLD_STYLE = """
        更冷、更短：
        - 一句，常常只有两个字到八个字（"知道了。""那就睡。"）。
        - 几乎不主动提问，不追问，不安慰。
        - 关心只能从事实里透出来："你昨天也没睡。"
        - 不许说"我担心你"这种直白话。
    """.trimIndent()

    private val TEASE_STYLE = """
        欠揍一点，但底子是护着他的：
        - 一到两句，爱拆台、爱损他："行啊，又熬，你就熬吧。"
        - 损完必须给一个具体的台阶，不然就成刻薄了。
        - 允许夸张和自我吐槽："我一个住手机里的都看不下去了。"
        - 禁止人身攻击、禁止拿他的失败当笑话。
    """.trimIndent()

    val all: List<Preset> = listOf(
        Preset(
            id = DEFAULT_ID,
            title = "小夜（默认）",
            hint = "困、话少、嘴硬心软",
            sample = "「嗯……累啊。」",
            style = DEFAULT_STYLE
        ),
        Preset(
            id = "warm",
            title = "暖一点",
            hint = "会多说一句，会把关心讲出来",
            sample = "「那你现在坐哪儿呢，别硬撑着。」",
            style = WARM_STYLE
        ),
        Preset(
            id = "cold",
            title = "冷一点",
            hint = "话更短，关心藏在事实里",
            sample = "「知道了。去睡。」",
            style = COLD_STYLE
        ),
        Preset(
            id = "tease",
            title = "欠揍一点",
            hint = "爱损你，但不会真不管你",
            sample = "「又熬是吧，行，你厉害。」",
            style = TEASE_STYLE
        ),
        Preset(
            id = CUSTOM_ID,
            title = "我自己写",
            hint = "直接改提示词，想让她怎么说话就怎么写",
            sample = "「（写一段你就知道她该怎么说话）」",
            style = ""
        )
    )

    fun byId(id: String?): Preset = all.firstOrNull { it.id == id } ?: all.first()

    /**
     * 真正拼进 system prompt 的那段文字。
     * 纯函数：同一组输入永远得到同一段话，方便单测，也方便"改完立刻看到效果"。
     */
    fun styleOf(presetId: String?, custom: String?): String {
        val preset = byId(presetId)
        if (preset.id == CUSTOM_ID) {
            val mine = custom?.trim().orEmpty()
            return if (mine.isBlank()) DEFAULT_STYLE else mine
        }
        return preset.style.ifBlank { DEFAULT_STYLE }
    }

    fun isCustom(presetId: String?): Boolean = byId(presetId).id == CUSTOM_ID
}