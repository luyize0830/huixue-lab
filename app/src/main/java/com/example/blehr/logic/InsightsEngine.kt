package com.example.blehr.logic

import com.example.blehr.model.ActionItem
import com.example.blehr.model.ActionStat
import com.example.blehr.model.EnergyBand
import com.example.blehr.model.ExperimentRecord
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** 某个时段的表现 */
data class BucketStat(
    val label: String,
    val count: Int,
    val avgRating: Double?
)

/**
 * 「回血图谱」背后的统计。
 *
 * 为什么要做这一层：产品主张是"帮你找出你自己的规律"，那只显示平均分是不够的，
 * 至少要回答三个问题——你什么时候最容易掉电、哪种行动对你最有效、心率到底降没降。
 */
data class Insights(
    val total: Int,
    val effectiveRate: Double,
    val hrDownRate: Double,
    val buckets: List<BucketStat>,
    val top: List<ActionStat>
)

object InsightsEngine {

    val BUCKET_LABELS = listOf("上午 6-12 点", "下午 12-18 点", "傍晚 18-23 点", "深夜 23-6 点")

    /** @return 0..3，对应 BUCKET_LABELS */
    fun bucketOf(hour: Int): Int = when (hour) {
        in 6..11 -> 0
        in 12..17 -> 1
        in 18..22 -> 2
        else -> 3
    }

    fun compute(records: List<ExperimentRecord>, pool: List<ActionItem>): Insights {
        if (records.isEmpty()) {
            return Insights(0, 0.0, 0.0, BUCKET_LABELS.map { BucketStat(it, 0, null) }, emptyList())
        }

        val effective = records.count { it.rating >= 4 }.toDouble() / records.size

        val hrPairs = records.mapNotNull { r ->
            val b = r.hrBefore
            val a = r.hrAfter
            if (b != null && a != null) (a - b) else null
        }
        val hrDown = if (hrPairs.isEmpty()) 0.0 else hrPairs.count { it < 0 }.toDouble() / hrPairs.size

        val buckets = (0..3).map { idx ->
            val inBucket = records.filter { bucketOf(hourOf(it.ts)) == idx }
            BucketStat(
                label = BUCKET_LABELS[idx],
                count = inBucket.size,
                avgRating = if (inBucket.isEmpty()) null else inBucket.sumOf { it.rating }.toDouble() / inBucket.size
            )
        }

        val top = Recommender.stats(pool, records)
            .filter { it.attempts >= 1 }
            .sortedWith(compareByDescending<ActionStat> { it.avgRating }.thenByDescending { it.attempts })
            .take(3)

        return Insights(records.size, effective, hrDown, buckets, top)
    }

    /** 一句能直接放在界面上的人话总结 */
    fun summaryLine(ins: Insights): String {
        if (ins.total == 0) return "还没有记录，做一次实验就能开始画你的图了。"
        val sb = StringBuilder()
        sb.append("你做了 ${ins.total} 次实验，其中 ${(ins.effectiveRate * 100).toInt()}% 觉得有用")
        val best = ins.buckets.filter { it.count > 0 }.maxByOrNull { it.avgRating ?: 0.0 }
        if (best != null && best.count >= 2) {
            sb.append("；${best.label} 效果最好（平均 ${fmt(best.avgRating ?: 0.0)} 分）")
        }
        if (ins.top.isNotEmpty()) {
            sb.append("；目前最吃「${ins.top.first().action.title}」")
        }
        return sb.toString() + "。"
    }

    fun hourOf(ts: Long): Int =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()).hour

    fun fmt(v: Double) = String.format("%.1f", v)
}

/**
 * 导出 CSV：这是"数据诚实/可带走"那条要求的实机体现——
 * 用户能把自己的记录拿走，才谈得上"数据属于你"。
 */
object RecordCsv {

    fun toCsv(records: List<ExperimentRecord>, pool: List<ActionItem>): String {
        val titleOf = pool.associate { it.id to it.title }
        val sb = StringBuilder()
        sb.append("时间,行动,当时状态,行动前心率,行动后心率,心率变化,评分(1-5)\n")
        records.sortedBy { it.ts }.forEach { r ->
            val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(r.ts), ZoneId.systemDefault())
                .toString().replace('T', ' ')
            val delta = if (r.hrBefore != null && r.hrAfter != null) (r.hrAfter - r.hrBefore).toString() else ""
            sb.append(cell(time)).append(',')
                .append(cell(titleOf[r.actionId] ?: r.actionId)).append(',')
                .append(cell(bandLabel(r.band))).append(',')
                .append(r.hrBefore ?: "").append(',')
                .append(r.hrAfter ?: "").append(',')
                .append(delta).append(',')
                .append(r.rating).append('\n')
        }
        return sb.toString()
    }

    private fun bandLabel(b: EnergyBand): String = b.label

    private fun cell(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"" + s.replace("\"", "\"\"") + "\"" else s
}