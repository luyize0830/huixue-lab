package com.example.blehr.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.blehr.logic.MascotMood
import kotlin.math.PI
import kotlin.math.sin

/**
 * 「小夜」的头像：蓝发、困眼、白蕾丝小头饰、蓝蝴蝶结。
 *
 * 为什么自己画（而不是买/找一张二次元图）：
 * 1. 版权干净 —— 参赛材料里出现的每一个像素都得是我们自己的；
 * 2. 尺寸自适应 —— 从 22dp 的对话头像到 96dp 的角色卡都画得出来；
 * 3. 情绪能跟着状态分变，而不是换一张贴图。
 *
 * 造型参考的是"睡不醒的蓝发少女"这个方向，但没有临摹任何现成角色。
 */
private val HairTop = Color(0xFF3C5F96)
private val HairDark = Color(0xFF2B4573)
private val HairLight = Color(0xFF5B84BE)
private val SkinBase = Color(0xFFFBE0CE)
private val SkinShade = Color(0xFFEFC7B0)
private val FrillWhite = Color(0xFFFBF8F2)
private val FrillShade = Color(0xFFE6DED1)
private val BowBlue = Color(0xFF6C9AD6)
private val LineInk = Color(0xFF1B2842)
private val IrisBlue = Color(0xFF7FB6EA)
private val BlushPink = Color(0xFFF0A79E)

@Composable
fun GirlMascot(
    mood: MascotMood = MascotMood.IDLE,
    size: Dp = 72.dp,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val period = if (mood == MascotMood.WORRIED) 2600 else 3600
    val t: Float = if (animated) {
        val transition = rememberInfiniteTransition(label = "girl")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = period),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bob"
        )
        v
    } else {
        0.5f
    }
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val bob = (t - 0.5f) * 0.03f * s
        val tilt = (t - 0.5f) * 3.2f
        rotate(degrees = tilt, pivot = Offset(s * 0.5f, s * 0.62f)) {
            drawGirl(s, mood, bob)
        }
    }
}

private fun DrawScope.drawGirl(s: Float, mood: MascotMood, bob: Float) {
    val cx = s * 0.5f
    val cy = s * 0.52f + bob

    // ---- 后面的长发 ----
    val back = Path().apply {
        moveTo(s * 0.20f, s * 0.26f)
        cubicTo(s * 0.20f, s * 0.06f, s * 0.80f, s * 0.06f, s * 0.80f, s * 0.26f)
        cubicTo(s * 0.88f, s * 0.52f, s * 0.84f, s * 0.78f, s * 0.74f, s * 0.94f)
        lineTo(s * 0.26f, s * 0.94f)
        cubicTo(s * 0.16f, s * 0.78f, s * 0.12f, s * 0.52f, s * 0.20f, s * 0.26f)
        close()
    }
    drawPath(back, HairDark)

    // ---- 脖子与领口 ----
    drawRoundRect(
        color = SkinShade,
        topLeft = Offset(cx - s * 0.085f, cy + s * 0.12f),
        size = Size(s * 0.17f, s * 0.16f),
        cornerRadius = CornerRadius(s * 0.05f)
    )
    drawRoundRect(
        color = FrillWhite,
        topLeft = Offset(cx - s * 0.19f, cy + s * 0.24f),
        size = Size(s * 0.38f, s * 0.16f),
        cornerRadius = CornerRadius(s * 0.07f)
    )
    // 领口蝴蝶结上的小宝石
    drawCircle(BowBlue, radius = s * 0.028f, center = Offset(cx, cy + s * 0.30f))

    // ---- 脸 ----
    drawOval(
        color = SkinBase,
        topLeft = Offset(cx - s * 0.235f, cy - s * 0.20f),
        size = Size(s * 0.47f, s * 0.46f)
    )

    // ---- 刘海（三簇）----
    val bangs = Path().apply {
        moveTo(cx - s * 0.245f, cy - s * 0.06f)
        cubicTo(cx - s * 0.26f, cy - s * 0.30f, cx - s * 0.10f, cy - s * 0.36f, cx, cy - s * 0.34f)
        cubicTo(cx + s * 0.12f, cy - s * 0.36f, cx + s * 0.26f, cy - s * 0.28f, cx + s * 0.245f, cy - s * 0.05f)
        // 右鬓角
        cubicTo(cx + s * 0.20f, cy + s * 0.02f, cx + s * 0.19f, cy - s * 0.08f, cx + s * 0.15f, cy - s * 0.10f)
        // 中分垂下的一缕
        cubicTo(cx + s * 0.09f, cy - s * 0.02f, cx + s * 0.05f, cy - s * 0.06f, cx + s * 0.02f, cy - s * 0.13f)
        cubicTo(cx - s * 0.03f, cy - s * 0.04f, cx - s * 0.07f, cy - s * 0.02f, cx - s * 0.11f, cy - s * 0.09f)
        cubicTo(cx - s * 0.15f, cy - s * 0.02f, cx - s * 0.19f, cy + s * 0.02f, cx - s * 0.245f, cy - s * 0.06f)
        close()
    }
    drawPath(bangs, HairTop)
    // 发顶高光
    drawPath(
        Path().apply {
            moveTo(cx - s * 0.10f, cy - s * 0.30f)
            cubicTo(cx - s * 0.02f, cy - s * 0.34f, cx + s * 0.08f, cy - s * 0.31f, cx + s * 0.12f, cy - s * 0.25f)
            cubicTo(cx + s * 0.04f, cy - s * 0.27f, cx - s * 0.04f, cy - s * 0.26f, cx - s * 0.10f, cy - s * 0.30f)
            close()
        },
        HairLight.copy(alpha = 0.55f)
    )

    // ---- 两侧鬓发（挡住一点脸，头型才不像个圆球）----
    val leftLock = Path().apply {
        moveTo(cx - s * 0.245f, cy - s * 0.14f)
        cubicTo(cx - s * 0.27f, cy + s * 0.02f, cx - s * 0.25f, cy + s * 0.16f, cx - s * 0.215f, cy + s * 0.24f)
        cubicTo(cx - s * 0.175f, cy + s * 0.20f, cx - s * 0.165f, cy + s * 0.02f, cx - s * 0.19f, cy - s * 0.16f)
        close()
    }
    drawPath(leftLock, HairTop)
    val rightLock = Path().apply {
        moveTo(cx + s * 0.245f, cy - s * 0.14f)
        cubicTo(cx + s * 0.27f, cy + s * 0.02f, cx + s * 0.25f, cy + s * 0.16f, cx + s * 0.215f, cy + s * 0.24f)
        cubicTo(cx + s * 0.175f, cy + s * 0.20f, cx + s * 0.165f, cy + s * 0.02f, cx + s * 0.19f, cy - s * 0.16f)
        close()
    }
    drawPath(rightLock, HairTop)

    // ---- 呆毛（睡不醒的人才有的那撮）----
    val ahoge = Path().apply {
        moveTo(cx + s * 0.02f, cy - s * 0.34f)
        cubicTo(cx + s * 0.05f, cy - s * 0.44f, cx + s * 0.12f, cy - s * 0.44f, cx + s * 0.10f, cy - s * 0.40f)
    }
    drawPath(ahoge, HairDark, style = Stroke(width = s * 0.022f, cap = StrokeCap.Round))

    // ---- 白色蕾丝头饰（一排半圆）----
    val frillY = cy - s * 0.30f
    for (i in 0..5) {
        val fx = cx - s * 0.175f + i * s * 0.07f
        drawCircle(FrillWhite, radius = s * 0.048f, center = Offset(fx, frillY))
        drawCircle(FrillShade.copy(alpha = 0.5f), radius = s * 0.048f, center = Offset(fx, frillY), style = Stroke(width = s * 0.007f))
    }
    drawRoundRect(
        color = FrillWhite,
        topLeft = Offset(cx - s * 0.20f, frillY),
        size = Size(s * 0.40f, s * 0.05f),
        cornerRadius = CornerRadius(s * 0.02f)
    )

    // ---- 蓝色蝴蝶结（左侧）----
    val bowX = cx - s * 0.26f
    val bowY = frillY + s * 0.02f
    drawOval(BowBlue, topLeft = Offset(bowX - s * 0.075f, bowY - s * 0.035f), size = Size(s * 0.075f, s * 0.07f))
    drawOval(BowBlue, topLeft = Offset(bowX + s * 0.0f, bowY - s * 0.035f), size = Size(s * 0.075f, s * 0.07f))
    drawCircle(HairDark, radius = s * 0.018f, center = Offset(bowX, bowY))

    // ---- 眼睛 ----
    val eyeY = cy - s * 0.02f
    val eyeDx = s * 0.093f
    when (mood) {
        MascotMood.HAPPY -> {
            // 弯弯的笑眼
            smileEye(cx - eyeDx, eyeY, s)
            smileEye(cx + eyeDx, eyeY, s)
        }
        else -> {
            sleepyEye(cx - eyeDx, eyeY, s, mood)
            sleepyEye(cx + eyeDx, eyeY, s, mood)
        }
    }

    // ---- 眉毛（担心时压下来）----
    if (mood == MascotMood.WORRIED) {
        drawLine(LineInk.copy(alpha = 0.75f), Offset(cx - eyeDx - s * 0.045f, eyeY - s * 0.075f), Offset(cx - eyeDx + s * 0.035f, eyeY - s * 0.055f), strokeWidth = s * 0.014f, cap = StrokeCap.Round)
        drawLine(LineInk.copy(alpha = 0.75f), Offset(cx + eyeDx + s * 0.045f, eyeY - s * 0.075f), Offset(cx + eyeDx - s * 0.035f, eyeY - s * 0.055f), strokeWidth = s * 0.014f, cap = StrokeCap.Round)
    }

    // ---- 腮红 ----
    val blushAlpha = if (mood == MascotMood.HAPPY) 0.55f else 0.4f
    drawOval(BlushPink.copy(alpha = blushAlpha), topLeft = Offset(cx - s * 0.185f, cy + s * 0.055f), size = Size(s * 0.075f, s * 0.042f))
    drawOval(BlushPink.copy(alpha = blushAlpha), topLeft = Offset(cx + s * 0.11f, cy + s * 0.055f), size = Size(s * 0.075f, s * 0.042f))

    // ---- 嘴 ----
    val mouth = Path().apply {
        moveTo(cx - s * 0.028f, cy + s * 0.105f)
        when (mood) {
            MascotMood.HAPPY -> cubicTo(cx - s * 0.012f, cy + s * 0.135f, cx + s * 0.012f, cy + s * 0.135f, cx + s * 0.028f, cy + s * 0.105f)
            MascotMood.WORRIED -> cubicTo(cx - s * 0.012f, cy + s * 0.09f, cx + s * 0.012f, cy + s * 0.09f, cx + s * 0.028f, cy + s * 0.108f)
            else -> cubicTo(cx - s * 0.012f, cy + s * 0.118f, cx + s * 0.012f, cy + s * 0.118f, cx + s * 0.028f, cy + s * 0.105f)
        }
    }
    drawPath(mouth, LineInk.copy(alpha = 0.55f), style = Stroke(width = s * 0.012f, cap = StrokeCap.Round))

    // ---- 情绪点缀 ----
    when (mood) {
        MascotMood.WORRIED -> {
            // 两滴汗
            drawCircle(BowBlue.copy(alpha = 0.75f), radius = s * 0.022f, center = Offset(cx + s * 0.235f, cy - s * 0.16f))
            drawCircle(BowBlue.copy(alpha = 0.45f), radius = s * 0.015f, center = Offset(cx + s * 0.27f, cy - s * 0.06f))
        }
        MascotMood.IDLE -> {
            // 睡意的 z（画小方折线，避免依赖字体）
            zee(cx + s * 0.24f, cy - s * 0.22f, s * 0.055f)
            zee(cx + s * 0.30f, cy - s * 0.32f, s * 0.04f)
        }
        MascotMood.HAPPY -> {
            drawCircle(HairLight.copy(alpha = 0.6f), radius = s * 0.026f, center = Offset(cx - s * 0.30f, cy - s * 0.20f))
            drawCircle(HairLight.copy(alpha = 0.4f), radius = s * 0.018f, center = Offset(cx + s * 0.31f, cy - s * 0.12f))
        }
    }
}

private fun DrawScope.sleepyEye(x: Float, y: Float, s: Float, mood: MascotMood) {
    val w = s * 0.150f
    val h = s * 0.125f
    val left = x - w / 2
    val top = y - h / 2

    // 眼白
    drawOval(Color(0xFFFDF9F4), topLeft = Offset(left, top), size = Size(w, h))
    // 虹膜偏下 —— 眼皮压着，看起来才像睡不醒
    val irisY = y + s * 0.012f
    drawCircle(IrisBlue, radius = s * 0.044f, center = Offset(x + s * 0.004f, irisY))
    drawCircle(Color(0xFF2A3B58), radius = s * 0.017f, center = Offset(x + s * 0.004f, irisY))
    drawCircle(Color.White.copy(alpha = 0.92f), radius = s * 0.010f, center = Offset(x - s * 0.007f, irisY - s * 0.010f))
    // 下眼睑一点点影
    drawArc(
        color = LineInk.copy(alpha = 0.18f),
        startAngle = 20f, sweepAngle = 140f, useCenter = false,
        topLeft = Offset(left, top), size = Size(w, h),
        style = Stroke(width = s * 0.008f)
    )

    // 上眼皮：用肤色把眼球上半盖掉（半闭眼的关键，不然就成了瞪眼娃娃）
    val lidY = y - h * 0.20f
    val lid = Path().apply {
        moveTo(left - s * 0.02f, top - s * 0.06f)
        lineTo(left + w + s * 0.02f, top - s * 0.06f)
        lineTo(left + w + s * 0.02f, lidY - s * 0.010f)
        cubicTo(x + w * 0.30f, lidY + s * 0.026f, x - w * 0.30f, lidY + s * 0.026f, left - s * 0.02f, lidY - s * 0.010f)
        close()
    }
    drawPath(lid, SkinBase)
    // 睫毛线（顺着眼皮的弧）
    val lash = Path().apply {
        moveTo(left - s * 0.012f, lidY - s * 0.008f)
        cubicTo(x - w * 0.30f, lidY + s * 0.028f, x + w * 0.30f, lidY + s * 0.028f, left + w + s * 0.012f, lidY - s * 0.008f)
    }
    drawPath(lash, LineInk, style = Stroke(width = s * 0.013f, cap = StrokeCap.Round))
    // 外眼角挑一下
    drawLine(
        LineInk,
        Offset(left + w + s * 0.008f, lidY - s * 0.012f),
        Offset(left + w + s * 0.034f, lidY - s * 0.042f),
        strokeWidth = s * 0.014f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.smileEye(x: Float, y: Float, s: Float) {
    val p = Path().apply {
        moveTo(x - s * 0.055f, y + s * 0.012f)
        cubicTo(x - s * 0.03f, y - s * 0.042f, x + s * 0.03f, y - s * 0.042f, x + s * 0.055f, y + s * 0.012f)
    }
    drawPath(p, LineInk, style = Stroke(width = s * 0.021f, cap = StrokeCap.Round))
}

private fun DrawScope.zee(x: Float, y: Float, w: Float) {
    val p = Path().apply {
        moveTo(x, y)
        lineTo(x + w, y)
        lineTo(x, y + w)
        lineTo(x + w, y + w)
    }
    drawPath(p, LineInk.copy(alpha = 0.35f), style = Stroke(width = w * 0.22f, cap = StrokeCap.Round))
}