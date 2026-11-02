package com.example.blehr.logic

import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.EnergyBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 人设是产品的一部分，所以不靠"看着顺眼"，靠断言钉住：
 * 她会记得人、会少废话、会在深夜更直接、不会自称 AI。
 */
class PersonaTest {

    private val pool = listOf(
        ActionItem("walk", "动", "下楼走一圈", 5, ActionCategory.MOVE, "绕楼一圈"),
        ActionItem("voice", "聊", "给朋友发语音", 4, ActionCategory.SOCIAL, "随便说两句")
    )

    @Test
    fun `第一次见面会自我介绍`() {
        val g = Persona.greeting(MascotMood.IDLE, 15, hasMemory = false, lastNote = null)
        assertTrue(g.contains(Persona.NAME))
        assertTrue(g.length in 10..80)
    }

    @Test
    fun `记得上次聊的事会追问`() {
        val g = Persona.greeting(MascotMood.IDLE, 15, hasMemory = true, lastNote = "在赶报告")
        assertTrue(g.contains("在赶报告"))
        assertTrue(g.contains("后来"))
    }

    @Test
    fun `担心时深夜更直接`() {
        val night = Persona.greeting(MascotMood.WORRIED, 2, hasMemory = true, lastNote = null)
        val day = Persona.greeting(MascotMood.WORRIED, 15, hasMemory = true, lastNote = null)
        assertNotEquals(night, day)
        assertTrue(night.contains("醒") || night.contains("睡") || night.contains("熬"))
    }

    @Test
    fun `系统提示词里写了名字与几条硬规矩`() {
        val sys = Persona.system(
            hour = 23,
            mood = MascotMood.WORRIED,
            memory = "昨天在赶报告",
            experimentDay = 3,
            totalRecords = 5
        )
        assertTrue(sys.contains(Persona.NAME))
        assertTrue(sys.contains("不要喊"))
        assertTrue(sys.contains("不装医生"))
        assertTrue(sys.contains("昨天在赶报告"))
        assertTrue(sys.contains("第 3 天"))
        assertTrue(sys.contains("actionIds"))
    }

    @Test
    fun `没记录时不编造实验进度`() {
        val sys = Persona.system(12, MascotMood.IDLE, null, 1, 0)
        assertTrue(sys.contains("还没做过实验"))
        assertFalse(sys.contains("第 1 天"))
    }

    @Test
    fun `离线回复要明说不是模型`() {
        val line = Persona.offlineLine(MascotMood.IDLE)
        assertTrue(line.contains("没连上模型"))
    }

    @Test
    fun `行动清单只给 id 名字时长类别`() {
        val u = Persona.user("累了", pool, 9, EnergyBand.MID)
        assertTrue(u.contains("walk｜下楼走一圈｜5分钟｜"))
        assertTrue(u.contains("9 点"))
        // 清单里不该出现 cue，省 token 也少干扰
        assertFalse(u.contains("绕楼一圈"))
    }
}