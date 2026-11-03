package com.example.blehr.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 这些断言的用途只有一个：保证"哪种错，说哪种话"。
 *
 * 起因是用户看到十几次"刚才没连上模型"，却不知道是自己没填 Key。
 * 401 和 404 的修法完全相反，混成一句话等于没说。
 */
class LlmErrorsTest {

    @Test
    fun `401 指向 Key 失效`() {
        val h = LlmErrors.hint(401)
        assertTrue(h, h.contains("Key"))
        assertTrue(h, h.contains("失效") || h.contains("不对"))
    }

    @Test
    fun `404 指向地址填错`() {
        val h = LlmErrors.hint(404)
        assertTrue(h, h.contains("地址"))
        assertTrue(h, h.contains("/v1"))
    }

    @Test
    fun `402 指向余额`() {
        assertTrue(LlmErrors.hint(402).contains("余额"))
    }

    @Test
    fun `429 让人等一会儿`() {
        val h = LlmErrors.hint(429)
        assertTrue(h, h.contains("频繁") || h.contains("等"))
    }

    @Test
    fun `5xx 归到服务端`() {
        val h = LlmErrors.hint(503)
        assertTrue(h, h.contains("服务端"))
        assertTrue(h, h.contains("503"))
    }

    @Test
    fun `没见过的状态码也带上原码`() {
        assertTrue(LlmErrors.hint(418).contains("418"))
    }

    @Test
    fun `域名解析失败说的是网络或拼写`() {
        val h = LlmErrors.fromException(UnknownHostException("api.deepseek.com"))
        assertTrue(h, h.contains("域名") || h.contains("网络"))
    }

    @Test
    fun `超时说的是没回应`() {
        val h = LlmErrors.fromException(SocketTimeoutException("timeout"))
        assertTrue(h, h.contains("没回应") || h.contains("超时") || h.contains("网络"))
    }

    @Test
    fun `SSL 失败提示用 https`() {
        assertTrue(LlmErrors.fromException(SSLException("handshake")).contains("https"))
    }

    @Test
    fun `未接入不是连不上`() {
        val h = LlmErrors.NOT_CONFIGURED
        assertTrue(h, h.contains("还没接入"))
        assertTrue(h, h.contains("本地规则"))
        // 这句里不该出现"连不上"——否则又变成用户分不清的那种话
        assertFalse(h, h.contains("连不上"))
    }

    @Test
    fun `所有提示都不是空话`() {
        val list = listOf(400, 401, 402, 403, 404, 429, 500, 418).map { LlmErrors.hint(it) } +
            listOf(LlmErrors.NOT_CONFIGURED, LlmErrors.BAD_REPLY)
        list.forEach {
            assertTrue(it, it.length >= 8)
        }
        assertEquals(10, list.size)
    }
}