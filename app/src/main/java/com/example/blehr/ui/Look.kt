package com.example.blehr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blehr.model.ActionCategory
import kotlin.math.min

/**
 * 视觉基调：暖白纸感 + 墨色字 + 陶土／麦黄／橄榄三个档位色。
 *
 * 刻意避开"深蓝黑底 + 霓虹薄荷绿 + 大圆角卡片"这套很常见的生成式界面语汇：
 * 一是那样看起来像模板而不是一个被人认真挑过颜色的产品，
 * 二是这个应用白天在户外用得多，深色底在强光下反而更难读。
 */
object Look {
    val Paper = Color(0xFFF7F4EF)
    val Card = Color(0xFFFFFFFF)
    val Card2 = Color(0xFFF1ECE4)
    val Ink = Color(0xFF201E1B)
    val InkSoft = Color(0xFF6E6659)
    val InkFaint = Color(0xFF9C9488)
    val Hair = Color(0xFFE3DBD0)
    val Track = Color(0xFFEDE7DD)

    val Clay = Color(0xFFBF5B3F)
    val Wheat = Color(0xFFB0842E)
    val Olive = Color(0xFF5A7A4C)

    val Accent = Color(0xFF3D5A4A)
    val OnAccent = Color(0xFFFBF9F5)
    // 大肥鱼用的蓝：不刺眼，压在暖白纸上像一支彩笔画的
    val Sea = Color(0xFF4A87C4)
    val SeaDeep = Color(0xFF2E5F93)
    val SeaBelly = Color(0xFFBBD7EC)
}

/** 自己画的图标，24dp 网格，统一线宽。不引图标库，避免包体再涨一截。 */
enum class GlyphKind {
    POOL, CHART, PEOPLE, CHAT, PULSE, BELL, CLOCK, MIC, BOLT, SPEAKER
}

@Composable
fun Glyph(
    kind: GlyphKind,
    size: Dp = 22.dp,
    tint: Color = Look.Ink,
    bg: Color = Look.Card,
    strokeScale: Float = 1f
) {
    Canvas(Modifier.size(size)) {
        val s = min(this.size.width, this.size.height)
        drawGlyph(kind, s, s * 0.085f * strokeScale, tint, bg)
    }
}

private fun DrawScope.drawGlyph(kind: GlyphKind, s: Float, sw: Float, tint: Color, bg: Color) {
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)

    fun roundRect(x1: Float, y1: Float, x2: Float, y2: Float, r: Float, filled: Boolean) {
        val topLeft = p(x1, y1)
        val w = (x2 - x1) * s
        val h = (y2 - y1) * s
        val radius = CornerRadius(r * s, r * s)
        if (filled) {
            drawRoundRect(tint, topLeft = topLeft, size = Size(w, h), cornerRadius = radius)
        } else {
            drawRoundRect(tint, topLeft = topLeft, size = Size(w, h), cornerRadius = radius, style = Stroke(sw))
        }
    }

    when (kind) {
        // 回血池：三行"清单"，比叠卡片更一眼看出是一组可做的事
        GlyphKind.POOL -> {
            val ys = floatArrayOf(0.26f, 0.50f, 0.74f)
            for (y in ys) {
                drawCircle(tint, radius = 0.055f * s, center = p(0.16f, y))
                line(0.34f, y, 0.88f, y)
            }
        }

        GlyphKind.CHART -> {
            line(0.12f, 0.84f, 0.88f, 0.84f)
            roundRect(0.22f, 0.52f, 0.36f, 0.84f, 0.05f, true)
            roundRect(0.43f, 0.34f, 0.57f, 0.84f, 0.05f, true)
            roundRect(0.64f, 0.62f, 0.78f, 0.84f, 0.05f, true)
        }

        GlyphKind.PEOPLE -> {
            drawCircle(tint, radius = 0.125f * s, center = p(0.32f, 0.30f), style = Stroke(sw))
            drawArc(
                tint, 180f, 180f, true,
                topLeft = p(0.06f, 0.52f),
                size = Size(0.52f * s, 0.42f * s),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
            drawCircle(tint, radius = 0.125f * s, center = p(0.68f, 0.30f), style = Stroke(sw))
            drawArc(
                tint, 180f, 180f, true,
                topLeft = p(0.42f, 0.52f),
                size = Size(0.52f * s, 0.42f * s),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }

        GlyphKind.CHAT -> {
            roundRect(0.10f, 0.16f, 0.90f, 0.70f, 0.18f, false)
            val tail = Path().apply {
                moveTo(0.30f * s, 0.70f * s)
                lineTo(0.26f * s, 0.90f * s)
                lineTo(0.50f * s, 0.70f * s)
            }
            drawPath(tail, tint, style = Stroke(sw, join = StrokeJoin.Round))
        }

        GlyphKind.PULSE -> {
            val path = Path().apply {
                moveTo(0.06f * s, 0.54f * s)
                lineTo(0.28f * s, 0.54f * s)
                lineTo(0.38f * s, 0.28f * s)
                lineTo(0.50f * s, 0.76f * s)
                lineTo(0.60f * s, 0.54f * s)
                lineTo(0.94f * s, 0.54f * s)
            }
            drawPath(path, tint, style = Stroke(sw, join = StrokeJoin.Round, cap = StrokeCap.Round))
        }

        GlyphKind.BELL -> {
            val bell = Path().apply {
                moveTo(0.24f * s, 0.70f * s)
                lineTo(0.24f * s, 0.44f * s)
                cubicTo(0.24f * s, 0.20f * s, 0.76f * s, 0.20f * s, 0.76f * s, 0.44f * s)
                lineTo(0.76f * s, 0.70f * s)
                close()
            }
            drawPath(bell, tint, style = Stroke(sw, join = StrokeJoin.Round))
            line(0.16f, 0.70f, 0.84f, 0.70f)
            drawArc(
                tint, 0f, 180f, false,
                topLeft = p(0.38f, 0.70f),
                size = Size(0.24f * s, 0.20f * s),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }

        GlyphKind.CLOCK -> {
            drawCircle(tint, radius = 0.36f * s, center = p(0.5f, 0.5f), style = Stroke(sw))
            line(0.5f, 0.5f, 0.5f, 0.28f)
            line(0.5f, 0.5f, 0.66f, 0.58f)
        }

        // 闪电：底部中央那个"现在就要一个办法"的按钮
        GlyphKind.BOLT -> {
            val path = Path().apply {
                moveTo(0.56f * s, 0.10f * s)
                lineTo(0.26f * s, 0.56f * s)
                lineTo(0.48f * s, 0.56f * s)
                lineTo(0.42f * s, 0.90f * s)
                lineTo(0.74f * s, 0.42f * s)
                lineTo(0.52f * s, 0.42f * s)
                close()
            }
            drawPath(path, tint)
        }

        GlyphKind.MIC -> {
            roundRect(0.36f, 0.12f, 0.64f, 0.56f, 0.14f, false)
            drawArc(
                tint, 0f, 180f, false,
                topLeft = p(0.24f, 0.40f),
                size = Size(0.52f * s, 0.44f * s),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
            line(0.5f, 0.84f, 0.5f, 0.94f)
            line(0.36f, 0.94f, 0.64f, 0.94f)
        }

        // 喇叭：实心梯形 + 两道声波。用在"念出来"的地方，比一个 🔊 更像自己画的。
        GlyphKind.SPEAKER -> {
            val horn = Path().apply {
                moveTo(0.12f * s, 0.38f * s)
                lineTo(0.27f * s, 0.38f * s)
                lineTo(0.44f * s, 0.20f * s)
                lineTo(0.44f * s, 0.80f * s)
                lineTo(0.27f * s, 0.62f * s)
                lineTo(0.12f * s, 0.62f * s)
                close()
            }
            drawPath(horn, tint)
            drawArc(
                tint, -58f, 116f, false,
                topLeft = p(0.46f, 0.34f),
                size = Size(0.22f * s, 0.32f * s),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
            drawArc(
                tint, -58f, 116f, false,
                topLeft = p(0.52f, 0.22f),
                size = Size(0.38f * s, 0.56f * s),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
    }
}

/** 分类用「一个汉字」的贴纸，而不是 17dp 的线稿图标——小尺寸下汉字一眼就认得。 */
fun ActionCategory.tagChar(): String = when (this) {
    ActionCategory.MOVE -> "动"
    ActionCategory.CALM -> "静"
    ActionCategory.SOCIAL -> "聊"
    ActionCategory.PLAY -> "玩"
    ActionCategory.REST -> "歇"
    ActionCategory.FUEL -> "吃"
}

@Composable
fun CategoryTag(category: ActionCategory, size: Dp = 34.dp, tint: Color = Look.Accent) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(tint.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            category.tagChar(),
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Medium,
            color = tint
        )
    }
}

/** 首页的入口块：图标 + 标题 + 一句解释。三块并排，手指够得着，也不用读箭头。 */
@Composable
fun EntryTile(
    kind: GlyphKind,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    tint: Color = Look.Accent,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Look.Card)
            .border(1.dp, Look.Hair, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Glyph(kind, size = 20.dp, tint = tint, bg = tint.copy(alpha = 0.10f))
        }
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Look.Ink)
        Text(hint, fontSize = 11.sp, color = Look.InkFaint, lineHeight = 15.sp)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Look.Hair))
}

@Composable
fun Dot(color: Color, size: Dp = 6.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(50)).background(color))
}

@Composable
fun SmallCaps(text: String, color: Color = Look.InkFaint) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 11.sp, color = color, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SpacerH(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun SpacerW(width: Dp) = Spacer(Modifier.width(width))