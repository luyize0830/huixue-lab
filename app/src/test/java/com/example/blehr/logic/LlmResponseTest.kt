package com.example.blehr.logic

import org.junit.Assert.*
import org.junit.Test

class LlmResponseTest {
    @Test fun `成功状态的网页不能算连上模型`() {
        assertTrue(LlmResponse.decode(200, "<html>Welcome</html>") is LlmResponse.Result.Fail)
    }

    @Test fun `必须是 choices 里的助手正文而不是任意 content`() {
        listOf(
            """{"content":"pong"}""",
            """{"choices":[]}""",
            """{"choices":[{"message":{"role":"user","content":"pong"}}]}""",
            """{"error":{"content":"not a reply"},"choices":[]}"""
        ).forEach { assertTrue(LlmResponse.decode(200, it) is LlmResponse.Result.Fail) }
    }

    @Test fun `空正文和非字符串正文都失败`() {
        listOf("null", "false", "123", "[]", "\"   \"").forEach { content ->
            assertTrue(LlmResponse.decode(200, """{"choices":[{"message":{"content":$content}}]}""") is LlmResponse.Result.Fail)
        }
    }

    @Test fun `正文是普通话也能通过`() {
        val r = LlmResponse.decode(200, """{"choices":[{"message":{"role":"assistant","content":"嗯，我在。"}}]}""") as LlmResponse.Result.Ok
        assertEquals("嗯，我在。", r.suggestion.reply)
    }

    @Test fun `结构化正文继续解析行动`() {
        val r = LlmResponse.decode(200, """{"choices":[{"message":{"content":"{\"reply\":\"喝口水\",\"actionIds\":[\"water\"]}"}}]}""") as LlmResponse.Result.Ok
        assertEquals(listOf("water"), r.suggestion.actionIds)
    }

    @Test fun `状态错误不能被看起来正常的正文盖掉`() {
        val r = LlmResponse.decode(401, """{"choices":[{"message":{"content":"pong"}}]}""") as LlmResponse.Result.Fail
        assertEquals(LlmErrors.hint(401), r.reason)
    }

    @Test fun `只回行动清单仍然判为无可用回复`() {
        assertTrue(LlmResponse.decode(200, """{"choices":[{"message":{"content":"water | 接水"}}]}""") is LlmResponse.Result.Fail)
    }
}
