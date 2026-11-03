package com.example.blehr.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ReplyBatch(
    val messages: List<ChatTurn>,
    val history: List<ChatTurn>,
    val minutesSinceLast: Int?
) {
    val text: String get() = ReplyGate.merge(messages.map { it.text })
}

/** 单个页面内串行处理消息：合批、固定历史快照、逐批失败兜底，取消不冒充失败。 */
class ChatReplyQueue(
    private val scope: CoroutineScope,
    private val history: () -> List<ChatTurn>,
    private val respond: suspend (ReplyBatch) -> Unit,
    private val onFailure: (ReplyBatch) -> Unit,
    private val onBusyChanged: (Boolean) -> Unit,
    private val now: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class Entry(val turn: ChatTurn, val sinceLast: Int?, val queuedAt: Long)
    private val pending = mutableListOf<Entry>()
    private var job: Job? = null

    // 与调用方一样在主线程使用；测试通过虚拟时钟驱动，不等待真实的八秒。
    fun enqueue(turn: ChatTurn, sinceLast: Int?) {
        pending.add(Entry(turn, sinceLast, now()))
        if (job?.isActive == true) return
        onBusyChanged(true)
        job = scope.launch {
            try {
                while (pending.isNotEmpty()) {
                    waitForBatch()
                    val entries = pending.toList()
                    pending.clear()
                    val messages = entries.map { it.turn }
                    // 这些消息已持久化；从最近的匹配项逐个剔除，避免在合并输入中重复发送。
                    val previous = history().toMutableList()
                    messages.asReversed().forEach { turn ->
                        val index = previous.indexOfLast { it == turn }
                        if (index >= 0) previous.removeAt(index)
                    }
                    val batch = ReplyBatch(messages, previous.toList(), entries.first().sinceLast)
                    try {
                        respond(batch)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        onFailure(batch)
                    }
                }
            } finally {
                onBusyChanged(false)
            }
        }
    }

    private suspend fun waitForBatch() {
        while (pending.isNotEmpty()) {
            val quietUntil = pending.last().queuedAt + ReplyGate.waitMs(pending.map { it.turn.text })
            val deadline = minOf(quietUntil, pending.first().queuedAt + ReplyGate.MAX_WAIT_MS)
            val remaining = deadline - now()
            if (remaining <= 0L) return
            delay(remaining)
        }
    }
}
