package com.example.blehr.logic

import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentParserTest {

    @Test fun `图书馆没有合适候选时也不能退回说话或躺下`() {
        val intent = IntentParser.parse("在图书馆", 15)
        val blocked = pool.filter { it.category in setOf(ActionCategory.SOCIAL, ActionCategory.REST) }
        val reply = ChatAdvisor.reply(intent, com.example.blehr.model.EnergyBand.MID, blocked, emptyList())
        assertTrue(reply.recommendations.isEmpty())
    }

    @Test fun `先过滤场景再推荐不会丢掉池中其他合适行动`() {
        val intent = IntentParser.parse("在图书馆", 15)
        val candidates = pool + item("water", 1, ActionCategory.FUEL)
        val reply = ChatAdvisor.reply(intent, com.example.blehr.model.EnergyBand.MID, candidates, emptyList())
        assertEquals(3, reply.recommendations.size)
        assertTrue(reply.recommendations.none { it.action.category in setOf(ActionCategory.SOCIAL, ActionCategory.REST) })
    }

    @Test
    fun `累的话识别为疲惫`() {
        val i = IntentParser.parse("我有点撑不住了", 15)
        assertEquals(Mood.TIRED, i.mood)
    }

    @Test
    fun `烦的话识别为焦虑`() {
        val i = IntentParser.parse("好焦虑，感觉赶不上进度", 15)
        assertEquals(Mood.ANXIOUS, i.mood)
    }

    @Test
    fun `麻木优先于累`() {
        val i = IntentParser.parse("累倒不累，就是学不进去，有点麻", 15)
        assertEquals(Mood.NUMB, i.mood)
    }

    @Test
    fun `识别地点`() {
        assertEquals(Scene.LIBRARY, IntentParser.parse("在图书馆坐着", 15).scene)
        assertEquals(Scene.DORM, IntentParser.parse("躺床上呢", 15).scene)
        assertEquals(Scene.COMMUTE, IntentParser.parse("地铁上，晃得难受", 15).scene)
        assertEquals(Scene.UNKNOWN, IntentParser.parse("我也不知道在哪", 15).scene)
    }

    @Test
    fun `识别分钟预算`() {
        assertEquals(10, IntentParser.parse("还有十分钟", 15).minutesBudget)
        assertEquals(5, IntentParser.parse("就 5 分钟", 15).minutesBudget)
    }

    @Test
    fun `识别小时预算并换算成分钟`() {
        assertEquals(60, IntentParser.parse("我还有一个小时", 15).minutesBudget)
        assertEquals(90, IntentParser.parse("大概 1.5 小时吧", 15).minutesBudget)
    }

    @Test
    fun `口语化说法按 5 分钟处理`() {
        assertEquals(5, IntentParser.parse("就一会儿", 15).minutesBudget)
    }

    @Test
    fun `没有时间词时预算为空而不是瞎猜`() {
        assertNull(IntentParser.parse("有点累", 15).minutesBudget)
    }

    @Test
    fun `深夜标记`() {
        assertTrue(IntentParser.parse("还早", 1).lateNight)
        assertTrue(IntentParser.parse("再学一会", 23).lateNight)
        assertTrue(!IntentParser.parse("下午了", 15).lateNight)
    }

    private fun item(id: String, minutes: Int, cat: ActionCategory) =
        ActionItem(id, "x", id, minutes, cat, "cue")

    private val pool = listOf(
        item("walk", 5, ActionCategory.MOVE),
        item("lie", 10, ActionCategory.REST),
        item("breath", 3, ActionCategory.CALM),
        item("call", 5, ActionCategory.SOCIAL)
    )

    @Test
    fun `回复里会带上时间预算的筛选说明`() {
        val intent = IntentParser.parse("还有十分钟，在图书馆", 15)
        val reply = ChatAdvisor.reply(intent, com.example.blehr.model.EnergyBand.MID, pool, emptyList())
        assertTrue(reply.text.contains("10 分钟以内"))
        assertTrue(reply.text.contains("图书馆") || reply.text.contains("排掉了"))
    }

    @Test
    fun `图书馆场景不会推要说话的行动`() {
        val intent = IntentParser.parse("在图书馆，有点累", 15)
        val reply = ChatAdvisor.reply(intent, com.example.blehr.model.EnergyBand.LOW, pool, emptyList())
        assertTrue(reply.recommendations.none { it.action.category == ActionCategory.SOCIAL })
    }

    @Test
    fun `预算太小时会老实说没有合适的`() {
        val intent = IntentParser.parse("我只有 1 分钟", 15)
        val reply = ChatAdvisor.reply(intent, com.example.blehr.model.EnergyBand.MID, pool, emptyList())
        assertTrue(reply.recommendations.isEmpty())
        assertTrue(reply.text.contains("放宽"))
    }
}
