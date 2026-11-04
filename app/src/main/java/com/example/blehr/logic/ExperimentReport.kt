package com.example.blehr.logic

import com.example.blehr.model.ActionCategory
import com.example.blehr.model.ActionItem
import com.example.blehr.model.ExperimentRecord
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 实验报告：把散落的记录汇成一张能拿去答辩的表。
 *
 * 三件事刻意做成这样：
 * 1. 分组只看两维（类别 / 时段），不搞多元回归 —— 样本量撑不起更花的分析；
 * 2. 每组少于 [MIN_GROUP] 次时只列不排名，避免"试过 1 次就下结论"；
 * 3. 报告里写清口径与局限，数字旁边永远带 n，不出现孤零零的百分比。
 */
data class ReportStat(
    val label: String,
    val n: Int,
    val avgRating: Double?,
    val effectiveRate: Double?,
    val avgHrDelta: Double?
)

data class ReportData(
    val total: Int,
    val days: Int,
    val overallRating: Double?,
    val effectiveRate: Double?,
    val hrDownRate: Double?,
    val byCategory: List<ReportStat>,
    val byHour: List<ReportStat>,
    val best: String?,
    val bestAvg: Double?,
    val worst: String?,
    val worstAvg: Double?,
    val enough: Boolean
) {
    val headline: String
        get() = when {
            total == 0 -> "还没有记录，先去试一次"
            !enough -> "已经 $total 次，攒到 $RECOMMEND_MIN 次再看规律更稳"
            best != null && bestAvg != null ->
                "目前对你最有效的是「$best」（平均 ${fmt(bestAvg)} 分，共 ${byCategory.first { it.label == best }.n} 次）"
            else -> "记录够多了，但还没有稳定占优的类别"
        }

    companion object {
        const val RECOMMEND_MIN = 10

        fun fmt(v: Double?): String =
            if (v == null) "—" else String.format(Locale.US, "%.1f", v)
    }
}

object ExperimentReport {

    /** 组内少于这个次数就不排名 */
    const val MIN_GROUP = 3

    val HOUR_LABELS = listOf("上午 6-12 点", "下午 12-18 点", "傍晚 18-23 点", "深夜 23-6 点")

    fun hourBucket(hour: Int): Int = when {
        hour in 6..11 -> 0
        hour in 12..17 -> 1
        hour in 18..22 -> 2
        else -> 3
    }

    private fun hourOf(ts: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return c.get(Calendar.HOUR_OF_DAY)
    }

    private fun dayKey(ts: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun statOf(label: String, list: List<ExperimentRecord>): ReportStat {
        val n = list.size
        if (n == 0) return ReportStat(label, 0, null, null, null)
        val avg = list.sumOf { it.rating }.toDouble() / n
        val eff = list.count { it.rating >= 4 } * 100.0 / n
        val deltas = list.mapNotNull { r ->
            val a = r.hrBefore
            val b = r.hrAfter
            if (a != null && b != null) b - a else null
        }
        val avgDelta = if (deltas.isEmpty()) null else deltas.sum().toDouble() / deltas.size
        return ReportStat(label, n, avg, eff, avgDelta)
    }

    fun build(records: List<ExperimentRecord>, pool: List<ActionItem>): ReportData {
        val byId = pool.associateBy { it.id }
        val catOf: (ExperimentRecord) -> ActionCategory = { r ->
            r.actionSnapshot?.category ?: byId[r.actionId]?.category ?: ActionCategory.MOVE
        }

        val categoryStats = ActionCategory.entries
            .map { cat -> statOf(cat.label, records.filter { catOf(it) == cat }) }
            .sortedWith(compareByDescending<ReportStat> { it.n }.thenByDescending { it.avgRating ?: 0.0 })

        val hourStats = (0..3).map { b ->
            statOf(HOUR_LABELS[b], records.filter { hourBucket(hourOf(it.ts)) == b })
        }

        // 只在样本够的组里排名，避免 1 次的组当冠军
        val rankable = categoryStats.filter { it.n >= MIN_GROUP && it.avgRating != null }
        val best = rankable.maxByOrNull { it.avgRating!! }
        val worst = rankable.minByOrNull { it.avgRating!! }

        val n = records.size
        val deltas = records.mapNotNull { r ->
            val a = r.hrBefore
            val b = r.hrAfter
            if (a != null && b != null) b - a else null
        }

        return ReportData(
            total = n,
            days = records.map { dayKey(it.ts) }.distinct().size,
            overallRating = if (n == 0) null else records.sumOf { it.rating }.toDouble() / n,
            effectiveRate = if (n == 0) null else records.count { it.rating >= 4 } * 100.0 / n,
            hrDownRate = if (deltas.isEmpty()) null else deltas.count { it < 0 } * 100.0 / deltas.size,
            byCategory = categoryStats,
            byHour = hourStats,
            best = best?.label,
            bestAvg = best?.avgRating,
            worst = worst?.label,
            worstAvg = worst?.avgRating,
            enough = n >= ReportData.RECOMMEND_MIN
        )
    }

    /** 能直接贴进 06 附录的 Markdown；口径与局限一起带上，不留给读者猜 */
    fun toMarkdown(records: List<ExperimentRecord>, pool: List<ActionItem>): String {
        val d = build(records, pool)
        val sb = StringBuilder()
        sb.appendLine("# 回血实验室 · 自我实验报告")
        sb.appendLine()
        sb.appendLine("> 由 App 内「实验报告」页导出。N=${d.total} 次，覆盖 ${d.days} 天。")
        sb.appendLine()
        if (d.total == 0) {
            sb.appendLine("还没有记录。")
            return sb.toString()
        }
        sb.appendLine("## 总体")
        sb.appendLine()
        sb.appendLine("| 指标 | 值 |")
        sb.appendLine("|---|---|")
        sb.appendLine("| 实验次数 | ${d.total} |")
        sb.appendLine("| 覆盖天数 | ${d.days} |")
        sb.appendLine("| 平均主观有效分（1-5） | ${ReportData.fmt(d.overallRating)} |")
        sb.appendLine("| 觉得有用的比例（≥4 分） | ${pct(d.effectiveRate)} |")
        sb.appendLine("| 心率下降的比例 | ${pct(d.hrDownRate)} |")
        sb.appendLine()
        sb.appendLine("## 按类别")
        sb.appendLine()
        sb.appendLine("| 类别 | n | 平均有效分 | 有效率 | 平均心率变化 |")
        sb.appendLine("|---|---|---|---|---|")
        d.byCategory.forEach { s ->
            sb.appendLine(
                "| ${s.label} | ${s.n} | ${ReportData.fmt(s.avgRating)} | ${pct(s.effectiveRate)} | " +
                    "${if (s.avgHrDelta == null) "—" else String.format(Locale.US, "%+.1f bpm", s.avgHrDelta)} |"
            )
        }
        sb.appendLine()
        sb.appendLine("## 按时段")
        sb.appendLine()
        sb.appendLine("| 时段 | n | 平均有效分 | 有效率 |")
        sb.appendLine("|---|---|---|---|")
        d.byHour.forEach { s ->
            sb.appendLine("| ${s.label} | ${s.n} | ${ReportData.fmt(s.avgRating)} | ${pct(s.effectiveRate)} |")
        }
        sb.appendLine()
        sb.appendLine("## 口径与局限")
        sb.appendLine()
        sb.appendLine("- 主观分是自评 1-5，存在天花板效应；心率来自标准 GATT 心率服务实时值，非手环历史数据。")
        sb.appendLine("- 每组少于 ${MIN_GROUP} 次时只列不排名。N=1 单一受试者，结论不可外推到他人。")
        sb.appendLine("- 本报告只描述现象，不做因果断言；不做显著性检验（样本量不足）。")
        return sb.toString()
    }

    private fun pct(v: Double?): String =
        if (v == null) "—" else "${v.roundToInt()}%"
}
