package com.example.blehr.logic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatReplyQueueTest {
    private class Fixture(val scope: TestScope) {
        val stored = mutableListOf<ChatTurn>()
        val replies = mutableListOf<ReplyBatch>()
        val failures = mutableListOf<ReplyBatch>()
        val busy = mutableListOf<Boolean>()
        var answer: suspend (ReplyBatch) -> Unit = { replies.add(it) }
        private val queue = ChatReplyQueue(
            scope.backgroundScope, { stored.toList() }, { answer(it) }, { failures.add(it) },
            { busy.add(it) }, { scope.testScheduler.currentTime }
        )
        fun send(text: String, since: Int? = null) {
            val turn = ChatTurn(true, text, scope.testScheduler.currentTime + 1)
            stored.add(turn)
            queue.enqueue(turn, since)
        }
        fun elapse(ms: Long) {
            scope.testScheduler.advanceTimeBy(ms)
            scope.testScheduler.runCurrent()
        }
    }

    @Test fun `连发合并而且所有本批消息都不再进入历史`() = runTest {
        val f = Fixture(this)
        val old = ChatTurn(false, "上次的话", 0)
        f.stored.add(old)
        f.send("第一句。", 20)
        f.send("第二句。", 0)
        f.elapse(8_000)
        assertEquals(1, f.replies.size)
        assertEquals("第一句。\n第二句。", f.replies.single().text)
        assertEquals(listOf(old), f.replies.single().history)
        assertEquals(20, f.replies.single().minutesSinceLast)
        assertEquals(listOf(true, false), f.busy)
    }

    @Test fun `继续输入后从最新一句计算安静窗口`() = runTest {
        val f = Fixture(this)
        f.send("一句。")
        f.elapse(4_000)
        f.send("两句。")
        f.elapse(4_000)
        assertTrue(f.replies.isEmpty())
        f.elapse(4_000)
        assertEquals(1, f.replies.size)
    }

    @Test fun `持续输入也不超过十四秒合批上限`() = runTest {
        val f = Fixture(this)
        f.send("一句。")
        f.elapse(7_000)
        f.send("两句。")
        f.elapse(6_000)
        f.send("三句。")
        f.elapse(1_000)
        assertEquals(3, f.replies.single().messages.size)
    }

    @Test fun `请求期间的新消息下一批处理且保留上一条回复`() = runTest {
        val f = Fixture(this)
        val release = CompletableDeferred<Unit>()
        f.answer = {
            f.replies.add(it)
            if (f.replies.size == 1) release.await()
            f.stored.add(ChatTurn(false, "收到。", testScheduler.currentTime + 1))
        }
        f.send("第一条。")
        f.elapse(8_000)
        f.send("请求中再说。")
        f.elapse(2_000)
        release.complete(Unit)
        f.elapse(6_000)
        assertEquals(2, f.replies.size)
        val second = f.replies.last()
        assertEquals("请求中再说。", second.text)
        assertEquals(listOf("第一条。", "收到。"), second.history.map { it.text })
    }

    @Test fun `出队后的请求失败仍兜底而且后续消息继续处理`() = runTest {
        val f = Fixture(this)
        f.answer = { throw IllegalStateException("test failure") }
        f.send("会失败。")
        f.elapse(8_000)
        assertEquals("会失败。", f.failures.single().text)
        f.answer = { f.replies.add(it) }
        f.send("还在吗。")
        f.elapse(8_000)
        assertEquals("还在吗。", f.replies.single().text)
        assertFalse(f.busy.last())
    }

    @Test fun `离开页面取消请求不伪造一条失败回复`() = runTest {
        val f = Fixture(this)
        f.answer = { awaitCancellation() }
        f.send("一句。")
        f.elapse(8_000)
        backgroundScope.cancel()
        f.elapse(0)
        assertTrue(f.failures.isEmpty())
        assertFalse(f.busy.last())
    }

    @Test fun `相同文字和时间戳只移除本批匹配次数`() = runTest {
        val f = Fixture(this)
        f.stored.add(ChatTurn(true, "重复。", 1))
        f.send("重复。")
        f.elapse(8_000)
        assertEquals(1, f.replies.single().history.size)
    }
}
