package com.example.blehr.logic

import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型输出是不可信的：可能带 ```json 围栏、可能前后加一句"好的，我来帮你"、
 * 可能把字段名写错。这里的每一条都是"遇到脏输出时按预期退化"。
 */
class LlmReplyParserTest {

    private val pool = listOf(
        ActionItem("walk", "动", "下楼走一圈", 5, ActionCategory.MOVE, "下楼，绕楼一圈"),
        ActionItem("breath", "静", "4-7-8 呼吸", 3, ActionCategory.CALM, "吸气4秒"),
        ActionItem("water", "吃", "接一杯水", 1, ActionCategory.FUEL, "慢慢喝完"),
        ActionItem("voice", "聊", "给朋友发语音", 4, ActionCategory.SOCIAL, "随便说两句")
    )

    @Test
    fun `标准 JSON 能解析`() {
        val s = LlmReplyParser.parse("""{"reply":"先站起来","actionIds":["walk","water"],"minutes":5}""")!!
        assertEquals("先站起来", s.reply)
        assertEquals(listOf("walk", "water"), s.actionIds)
        assertEquals(5, s.minutes)
    }

    @Test
    fun `带代码围栏也能解析`() {
        val raw = """
            ```json
            {"reply":"图书馆就别说话了","actionIds":["breath"],"minutes":3}
            ```
        """.trimIndent()
        val s = LlmReplyParser.parse(raw)!!
        assertEquals("图书馆就别说话了", s.reply)
        assertEquals(listOf("breath"), s.actionIds)
    }

    @Test
    fun `前后有客套话也能截出对象`() {
        val raw = "好的，我来帮你挑：{\"reply\":\"试试看\",\"actionIds\":[\"walk\"]} 希望有用"
        val s = LlmReplyParser.parse(raw)!!
        assertEquals("试试看", s.reply)
        assertNull(s.minutes)
    }

    @Test
    fun `字符串里的花括号不会把对象截断`() {
        val raw = """{"reply":"他说「{别硬撑}」","actionIds":["walk"]}"""
        val s = LlmReplyParser.parse(raw)!!
        assertEquals("他说「{别硬撑}」", s.reply)
    }

    @Test
    fun `缺 reply 或 actionIds 一律返回 null`() {
        assertNull(LlmReplyParser.parse("""{"actionIds":["walk"]}"""))
        assertNull(LlmReplyParser.parse("""{"reply":"试试"}"""))
        assertNull(LlmReplyParser.parse("""{"reply":"","actionIds":["walk"]}"""))
        assertNull(LlmReplyParser.parse("完全不是 JSON"))
        assertNull(LlmReplyParser.parse(""))
        assertNull(LlmReplyParser.parse(null))
    }

    @Test
    fun `minutes 越界当作没有`() {
        val s = LlmReplyParser.parse("""{"reply":"x","actionIds":["walk"],"minutes":999}""")!!
        assertNull(s.minutes)
    }

    @Test
    fun `pick 会丢掉池子里没有的 id 并去重保序`() {
        val picked = LlmReplyParser.pick(pool, listOf("walk", "不存在", "water", "walk"))
        assertEquals(listOf("walk", "water"), picked.map { it.id })
    }

    @Test
    fun `pick 最多给三个`() {
        val picked = LlmReplyParser.pick(pool, listOf("walk", "breath", "water", "voice"))
        assertEquals(3, picked.size)
    }

    @Test
    fun `chat 响应里取 content 再解析`() {
        val response = """
            {"id":"x","choices":[{"index":0,"message":{"role":"assistant",
            "content":"{\"reply\":\"先喝口水\",\"actionIds\":[\"water\"],\"minutes\":1}"}}]}
        """.trimIndent()
        val content = JsonLite.readString(response, "content")!!
        val s = LlmReplyParser.parse(content)!!
        assertEquals("先喝口水", s.reply)
        assertEquals(listOf("water"), s.actionIds)
    }

    @Test
    fun `转义字符按 JSON 规则还原`() {
        val obj = """{"reply":"第一行\n第二行 \"引号\" \u4e2d"}"""
        assertEquals("第一行\n第二行 \"引号\" 中", JsonLite.readString(obj, "reply"))
    }

    @Test
    fun `提示词里带上时间和清单`() {
        val u = LlmPrompt.user("有点累", pool, 23, com.example.blehr.model.EnergyBand.LOW)
        assertTrue(u.contains("23 点"))
        assertTrue(u.contains("有点累"))
        assertTrue(u.contains("walk"))
        assertTrue(u.contains("低电量") || u.contains("低"))
    }
}