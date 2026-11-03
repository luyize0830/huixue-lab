package com.example.blehr.data

import com.example.blehr.logic.LlmErrors
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LlmClientTest {
    private fun probe(code: Int, body: String): LlmClient.TestResult {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return try {
            runBlocking { LlmClient.test("http://127.0.0.1:${server.address.port}/v1", "test-model", "test-key") }
        } finally {
            server.stop(0)
        }
    }

    @Test fun `连接测试拒绝实际返回二百的网页`() {
        assertFalse(probe(200, "<html>gateway</html>").ok)
    }

    @Test fun `连接测试接收真实聊天响应`() {
        assertTrue(probe(200, """{"choices":[{"message":{"role":"assistant","content":"pong"}}]}""").ok)
    }

    @Test fun `连接测试和正式聊天使用相同错误提示`() {
        val result = probe(401, "{}")
        assertFalse(result.ok)
        assertEquals(LlmErrors.hint(401), result.message)
    }
}
