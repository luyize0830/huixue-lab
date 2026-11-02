package com.example.blehr.logic

/**
 * 角色情绪。放在 logic 层是因为它同时被"形象"（ui）和"说话"（logic/Persona）消费，
 * 只能有一份阈值，否则鱼说"我还好"、气泡说"我担心你"，会露馅。
 */
enum class MascotMood { HAPPY, IDLE, WORRIED }

object MoodRule {
    /** 比档位更早一步：档位负责如实汇报，角色负责先劝一句 */
    const val WORRIED_BELOW = 70
    const val HAPPY_FROM = 85

    fun of(score: Int): MascotMood = when {
        score < WORRIED_BELOW -> MascotMood.WORRIED
        score >= HAPPY_FROM -> MascotMood.HAPPY
        else -> MascotMood.IDLE
    }

    fun label(mood: MascotMood): String = when (mood) {
        MascotMood.HAPPY -> "心情不错"
        MascotMood.IDLE -> "有点困"
        MascotMood.WORRIED -> "在担心你"
    }
}