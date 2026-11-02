package com.example.blehr.logic

/** 一轮对话。fromUser=false 表示是角色说的。 */
data class ChatTurn(val fromUser: Boolean, val text: String, val ts: Long)

/**
 * 一次请求要带上来的全部上下文。做成一个对象而不是八个参数，
 * 是因为以后加字段（比如"上周的平均分"）不用改所有调用点。
 */
data class ChatContext(
    val hour: Int,
    val band: com.example.blehr.model.EnergyBand,
    val mood: MascotMood,
    val memory: String? = null,
    val experimentDay: Int = 1,
    val totalRecords: Int = 0,
    val nickname: String = "",
    /** 用户调过的说话方式（见 PersonaPreset）；空 = 用默认 */
    val style: String = "",
    val history: List<ChatTurn> = emptyList()
)