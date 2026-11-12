package com.example.blehr.model

/**
 * 精力档位：用于把连续信号离散化为"现在大概在哪个状态"。
 * 注意：这不是医学判断，只是产品层的工作档位。
 */
enum class EnergyBand(val label: String) {
    LOW("低电量"),
    MID("还有余量"),
    HIGH("状态充足")
}

/** 回血行动的类别，用于"哪类行动对我有效"的横向对比 */
enum class ActionCategory(val label: String) {
    MOVE("动一动"),
    CALM("静下来"),
    SOCIAL("找人"),
    PLAY("玩一下"),
    REST("闭眼"),
    FUEL("吃点喝点")
}

/** 一个可执行的微行动（必须能在 10 分钟内做完） */
data class ActionItem(
    val id: String,
    val emoji: String,
    val title: String,
    val minutes: Int,
    val category: ActionCategory,
    val cue: String
)

/** 一次传感器快照：所有字段都可能缺失，缺失时降级而不是报错 */
data class SensorSnapshot(
    val heartRate: Int? = null,
    val restingHr: Int? = null,
    val focusMinutes: Int = 0,
    val screenMinutesLastHour: Int = 0,
    val hourOfDay: Int = 12,
    /** 已经多久没起身走动（分钟）；拿不到就是 null，不猜 */
    val sedentaryMinutes: Int? = null,
    /** 昨夜睡眠总时长（分钟，来自 Health Connect）；拿不到就是 null，不猜 */
    val sleepMinutesLastNight: Int? = null
)

/** 状态评估结果：分数 + 人话结论 + 可展开的依据 */
data class StateAssessment(
    val band: EnergyBand,
    val score: Int,
    val headline: String,
    val reasons: List<String>
)

/** 一次"实验"记录：行动 + 当时的档位 + 前后心率 + 主观有效性 */
data class ExperimentRecord(
    val actionId: String,
    val band: EnergyBand,
    val hrBefore: Int?,
    val hrAfter: Int?,
    val rating: Int,
    val ts: Long,
    val sessionId: String = "",
    val actionSnapshot: ActionItem? = null
)

/** 聚合统计：用于回血图谱与推荐理由 */
data class ActionStat(
    val action: ActionItem,
    val attempts: Int,
    val avgRating: Double,
    val avgHrDelta: Double?
)
