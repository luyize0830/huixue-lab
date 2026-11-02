package com.example.blehr.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 说话方式是用户能改的一项配置，所以它得是可测的：
 * 选谁、写了什么、没写的时候退回到哪 —— 这三件事不能靠"打开 App 看一眼"。
 */
class PersonaPresetTest {

    @Test
    fun `每个预设都有说话方式，除了自己写`() {
        PersonaPreset.all.forEach { p ->
            if (p.id == PersonaPreset.CUSTOM_ID) {
                assertTrue("自定义档不该内置提示词", p.style.isBlank())
            } else {
                assertTrue("${p.title} 缺说话方式", p.style.length > 40)
            }
        }
    }

    @Test
    fun `id 不认识时退回默认档`() {
        assertEquals(PersonaPreset.DEFAULT_ID, PersonaPreset.byId("不存在的").id)
        assertEquals(PersonaPreset.DEFAULT_ID, PersonaPreset.byId(null).id)
    }

    @Test
    fun `选了自定义就用用户写的`() {
        val mine = "像室友一样说话，别安慰我。"
        assertEquals(mine, PersonaPreset.styleOf(PersonaPreset.CUSTOM_ID, mine))
    }

    @Test
    fun `选了自定义但一个字没写 退回默认`() {
        val fallback = PersonaPreset.styleOf(PersonaPreset.DEFAULT_ID, null)
        assertEquals(fallback, PersonaPreset.styleOf(PersonaPreset.CUSTOM_ID, "   "))
        assertEquals(fallback, PersonaPreset.styleOf(PersonaPreset.CUSTOM_ID, null))
    }

    @Test
    fun `不同预设给出来的提示词不一样`() {
        val a = PersonaPreset.styleOf("cold", null)
        val b = PersonaPreset.styleOf("warm", null)
        assertFalse(a == b)
        assertTrue(a.contains("两个字") || a.contains("更短"))
        assertTrue(b.contains("关心") || b.contains("软"))
    }
}