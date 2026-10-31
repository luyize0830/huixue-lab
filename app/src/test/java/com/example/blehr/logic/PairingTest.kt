package com.example.blehr.logic

import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {

    private val pool = listOf(
        ActionItem("walk", "🚶", "下楼走一圈", 5, ActionCategory.MOVE, "戴上耳机下楼"),
        ActionItem("water", "💧", "接一杯水慢慢喝", 1, ActionCategory.FUEL, "接满一杯"),
        ActionItem("lie", "😴", "平躺闭眼 10 分钟", 10, ActionCategory.REST, "定个闹钟")
    )

    private val now = 1_790_000_000_000L // 固定时间，避免用例随真实时间漂移

    @Test
    fun `编码后再解码能还原行动与分钟数`() {
        val code = Pairing.encode(pool[1], 3, now - 30_000L)
        val decoded = Pairing.decode(code, pool, now)
        assertNotNull(decoded)
        assertEquals("water", decoded!!.actionId)
        assertEquals(3, decoded.minutes)
    }

    @Test
    fun `短码可以被念出来，带分隔符也能解析`() {
        val code = Pairing.encode(pool[0], 5, now)
        val pretty = Pairing.pretty(code)
        assertTrue(pretty.length == 9 && pretty[4] == '-')
        assertEquals(code.padStart(8, '0'), Pairing.normalize(pretty))
        assertNotNull(Pairing.decode(Pairing.normalize(pretty), pool, now))
    }

    @Test
    fun `小写和空格不该让码失效`() {
        val code = Pairing.encode(pool[2], 8, now - 5_000L)
        val messy = " " + code.lowercase() + " "
        assertNotNull(Pairing.decode(messy, pool, now))
    }

    @Test
    fun `超过两小时的码视为过期`() {
        val code = Pairing.encode(pool[0], 5, now - 3 * 60 * 60 * 1000L)
        assertNull(Pairing.decode(code, pool, now))
    }

    @Test
    fun `未来时间码不认`() {
        val code = Pairing.encode(pool[0], 5, now + 10 * 60 * 1000L)
        assertNull(Pairing.decode(code, pool, now))
    }

    @Test
    fun `池子里没有的行动解析成 null`() {
        val code = Pairing.encode(ActionItem("ghost", "👻", "不存在", 3, ActionCategory.CALM, ""), 3, now)
        assertNull(Pairing.decode(code, pool, now))
    }

    @Test
    fun `乱输的东西不会崩`() {
        assertNull(Pairing.decode("", pool, now))
        assertNull(Pairing.decode("!!!!", pool, now))
        assertNull(Pairing.decode("ZZZZZZZZZZZZZZ", pool, now))
    }

    @Test
    fun `剩余时间随开始时间递减，并且不会变成负数`() {
        val start = now - 90_000L
        assertEquals(90, Pairing.remainingSeconds(3, start, now))
        assertEquals(0, Pairing.remainingSeconds(1, now - 10 * 60_000L, now))
        assertTrue(Pairing.expired(1, now - 10 * 60_000L, now))
    }

    @Test
    fun `分享文案里带得上行动名、分钟数和码`() {
        val text = Pairing.shareText(pool[1], 3, "ABCD1234")
        assertTrue(text.contains("接一杯水慢慢喝"))
        assertTrue(text.contains("3 分钟"))
        assertTrue(text.contains("ABCD1234"))
    }
}