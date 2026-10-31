package com.example.blehr.logic

import com.example.blehr.model.ActionItem

/**
 * 陪练码：一个可以口述、可以粘贴的短码，把"哪个行动、几分钟、几点开始"打包进去。
 *
 * 为什么不写二维码：二维码需要相机权限和一个编码库，而答辩现场真正需要的是
 * "我说一句、你在另一台手机上输进去" 这种最低摩擦的方式。短码也能顺手丢进微信。
 *
 * 编码格式（40 bit，Base36 大写 8 位）：
 *   bit 39..37 版本（固定 1）
 *   bit 36..31 分钟数（1..63）
 *   bit 30..16 行动 id 的 15 位哈希
 *   bit 15..0  开始时间（epoch 分钟，取低 16 位 ≈ 45 天回绕）
 */
object Pairing {

    private const val VERSION = 1
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val MINUTE_WRAP = 65536L
    /** 码的有效期：开始时间最多往回 2 小时，超出视为过期（避免误接到几天前的一局）。 */
    const val MAX_AGE_MS = 2 * 60 * 60 * 1000L

    data class Code(val actionId: String, val minutes: Int, val startAtMs: Long)

    fun encode(action: ActionItem, minutes: Int, startAtMs: Long): String {
        val h = action.id.hashCode() and 0x7FFF
        val ep = (startAtMs / 60_000L).mod(MINUTE_WRAP)
        val value = (VERSION.toLong() shl 37) or
            (minutes.coerceIn(1, 63).toLong() shl 31) or
            (h.toLong() shl 16) or ep
        return toBase36(value)
    }

    /** 解析失败、过期、或池子里找不到对应行动时返回 null——宁可让用户重输，也不要瞎猜。 */
    fun decode(code: String, pool: List<ActionItem>, now: Long): Code? {
        val v = fromBase36(code.removeSuffix(" "))
        if (v == null) return null
        val version = ((v shr 37) and 0x7).toInt()
        if (version != VERSION) return null
        val minutes = ((v shr 31) and 0x3F).toInt()
        if (minutes !in 1..63) return null
        val h = ((v shr 16) and 0x7FFF).toInt()
        val ep = v and 0xFFFF

        val nowMin = (now / 60_000L).mod(MINUTE_WRAP)
        var delta = ep - nowMin
        if (delta > MINUTE_WRAP / 2) delta -= MINUTE_WRAP
        if (delta < MINUTE_WRAP / -2) delta += MINUTE_WRAP
        val startAt = now + delta * 60_000L
        if (startAt > now + 60_000L) return null
        if (now - startAt > MAX_AGE_MS) return null

        val action = pool.firstOrNull { (it.id.hashCode() and 0x7FFF) == h } ?: return null
        return Code(action.id, minutes, startAt)
    }

    fun remainingSeconds(minutes: Int, startAtMs: Long, now: Long): Int =
        (minutes * 60L - (now - startAtMs) / 1000L).coerceIn(0L, 63 * 60L).toInt()

    fun expired(minutes: Int, startAtMs: Long, now: Long): Boolean =
        now - startAtMs > minutes * 60_000L

    fun shareText(action: ActionItem, minutes: Int, code: String): String =
        "我在做「${action.title}」${minutes} 分钟，陪我一下？\n陪练码 $code\n（在回血实验室首页点「用陪练码加入」）"

    /** 8 位定长：左补 0，方便用户按 4+4 念。 */
    fun pretty(code: String): String {
        val c = code.padStart(8, '0').takeLast(8)
        return c.substring(0, 4) + "-" + c.substring(4)
    }

    fun normalize(input: String): String =
        input.uppercase().filter { it in ALPHABET }

    private fun toBase36(value: Long): String {
        var v = value
        val sb = StringBuilder(8)
        repeat(8) {
            sb.append(ALPHABET[(v % 36).toInt()])
            v /= 36
        }
        return sb.reverse().toString()
    }

    private fun fromBase36(input: String): Long? {
        val s = normalize(input)
        if (s.isEmpty() || s.length > 8) return null
        var v = 0L
        for (c in s) {
            val d = ALPHABET.indexOf(c)
            if (d < 0) return null
            v = v * 36 + d
        }
        return v
    }
}