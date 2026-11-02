package com.example.blehr.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanizerTest {

    @Test
    fun `按竖线拆成多条`() {
        val out = Humanizer.split("诶……我平时不太刷那些呀||你昨天发的那只猫，我倒是还留着呢")
        assertEquals(2, out.size)
        assertEquals("诶……我平时不太刷那些呀", out[0])
        assertEquals("你昨天发的那只猫，我倒是还留着呢", out[1])
    }

    @Test
    fun `全角竖线和换行也能拆`() {
        val out = Humanizer.split("嗯｜｜行吧")
        assertEquals(listOf("嗯", "行吧"), out)
        val nl = Humanizer.split("嗯\n行吧")
        assertEquals(listOf("嗯", "行吧"), nl)
    }

    @Test
    fun `超过三条只留三条`() {
        val out = Humanizer.split("一||二||三||四||五")
        assertEquals(Humanizer.MAX_BUBBLES, out.size)
        assertEquals("一", out[0])
        assertEquals("二", out[1])
        assertTrue(out[2].contains("三"))
    }

    @Test
    fun `短的三句合成一条，长段落才按句子拆开`() {
        // 短话本来就像一条消息，不该为了"看起来像真人"硬拆成三条
        val short = Humanizer.split("今天有点冷。你多穿点。别又穿那件单的。")
        assertEquals(1, short.size)

        val long = Humanizer.split("今天下午一直在想那个事，越想越觉得没意思。可能是最近太闲了吧，也可能是别的。")
        assertTrue(long.size >= 2)
        assertTrue(long.none { it.length > Humanizer.MAX_CHARS })
    }

    @Test
    fun `去掉句尾句号`() {
        val out = Humanizer.split("知道了。||那就睡。")
        assertEquals(listOf("知道了", "那就睡"), out)
    }

    @Test
    fun `整句是客服腔会被丢掉`() {
        val out = Humanizer.split("建议你先休息一下。||我也没睡。")
        assertEquals(listOf("我也没睡"), out)
    }

    @Test
    fun `全是客服腔时返回空列表表示这次不说话`() {
        val out = Humanizer.split("总之建议你早点睡，希望能帮到你。")
        assertTrue(out.isEmpty())
    }

    @Test
    fun `空回复返回空列表`() {
        assertTrue(Humanizer.split(null).isEmpty())
        assertTrue(Humanizer.split("   ").isEmpty())
    }

    @Test
    fun `单条超过上限会在逗号处截断`() {
        val long = "我今天下午一直在想那个事情，越想越觉得没什么意思，可能是我最近太闲了"
        val out = Humanizer.split(long)
        assertTrue(out.all { it.length <= Humanizer.MAX_CHARS })
        assertFalse(out.isEmpty())
    }

    @Test
    fun `三个字以内的短回复原样保留`() {
        assertEquals(listOf("嗯"), Humanizer.split("嗯。"))
        assertEquals(listOf("……"), Humanizer.split("……"))
    }
}