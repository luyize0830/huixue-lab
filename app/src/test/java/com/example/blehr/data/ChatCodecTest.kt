package com.example.blehr.data

import com.example.blehr.logic.ChatTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记忆的存储格式是自己定的，所以必须钉住：改格式会让老用户的对话读不出来。
 */
class ChatCodecTest {

    @Test
    fun `编解码能原样还原`() {
        val turns = listOf(
            ChatTurn(true, "在图书馆，还有一小时", 1_700_000_000_000),
            ChatTurn(false, "那就别说话了，闭眼三分钟", 1_700_000_060_000)
        )
        val back = ChatCodec.decode(ChatCodec.encode(turns))
        assertEquals(2, back.size)
        assertEquals(turns, back)
        assertEquals(true, back[0].fromUser)
        assertEquals(false, back[1].fromUser)
    }

    @Test
    fun `换行 tab 反斜杠都能安全存取`() {
        val tricky = ChatTurn(false, "第一行\n第二行\t带制表符\\和反斜杠", 1L)
        val back = ChatCodec.decode(ChatCodec.encode(listOf(tricky)))
        assertEquals(1, back.size)
        assertEquals(tricky.text, back[0].text)
    }

    @Test
    fun `坏行被跳过而不是让整份记忆失效`() {
        // 第二行时间戳不是数字，第三、四行根本没有制表符
        val raw = "u\t1\t好的\na\t不是数字\t坏的\n这不是一行\n方案在下面\na\t3\t最后一句"
        val turns = ChatCodec.decode(raw)
        assertEquals(2, turns.size)
        assertEquals("好的", turns[0].text)
        assertEquals("最后一句", turns[1].text)
    }

    @Test
    fun `空输入返回空列表`() {
        assertTrue(ChatCodec.decode(null).isEmpty())
        assertTrue(ChatCodec.decode("").isEmpty())
        assertTrue(ChatCodec.encode(emptyList()).isEmpty())
    }

    @Test
    fun `天数的第一天是 1 而不是 0`() {
        val now = System.currentTimeMillis()
        assertEquals(1, ChatMemory.dayIndex(null, now))
        assertEquals(1, ChatMemory.dayIndex(now, now))
    }

    @Test
    fun `跨两天算第二天`() {
        val dayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        assertEquals(2, ChatMemory.dayIndex(now - dayMs, now))
        assertEquals(3, ChatMemory.dayIndex(now - 2 * dayMs, now))
    }

    @Test
    fun `dayKey 是 yyyy-MM-dd`() {
        val key = ChatMemory.dayKey(1_700_000_000_000)
        assertEquals(10, key.length)
        assertEquals('-', key[4])
        assertEquals('-', key[7])
    }
}