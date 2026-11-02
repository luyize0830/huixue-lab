package com.example.blehr.ui

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 蓝色大肥鱼。
 *
 * 为什么是它：用户接上自己的 API 键之后，对话框那头发生的东西就有了"形状"，
 * 一条会动、会累、会劝你歇一会儿的鱼，比一个"AI 助手"的灰色头像更让人愿意打开它。
 *
 * 全部用 Canvas 画，没有贴图：一是包体小，二是要跟 v0.5 起"暖白纸 + 手绘图"的调子一致。
 * 三种情绪由状态分数决定：够了（开心）/ 还行（发呆）/ 撑不住了（担心）。
 */
enum class FishMood { HAPPY, IDLE, WORRIED }

/**
 * 鱼的情绪规则：故意比「档位」更严格（担心 <70，开心 ≥85）。
 * 档位负责如实汇报，鱼负责早一点劝你 —— 67 分界面还写着「还有余量」，鱼已经在担心了。
 * 抽成纯函数是为了能单测，界面里只负责调用。
 */
object FishMoodRule {
    const val WORRIED_BELOW = 70
    const val HAPPY_FROM = 85

    fun of(score: Int): FishMood = when {
        score < WORRIED_BELOW -> FishMood.WORRIED
        score >= HAPPY_FROM -> FishMood.HAPPY
        else -> FishMood.IDLE
    }
}

@Composable
fun FishMascot(
    mood: FishMood = FishMood.IDLE,
    size: Dp = 72.dp,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "fish")
    val t by if (animated) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (mood == FishMood.WORRIED) 2600 else 3400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "t"
        )
    } else {
        androidx.compose.runtime.mutableFloatStateOf(0f)
    }

    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val bob = sin(t * 2f * PI.toFloat()) * s * 0.022f
        val tilt = sin(t * 2f * PI.toFloat()) * 2.2f
        drawFish(s, mood, bob, tilt)
    }
}

private fun DrawScope.drawFish(s: Float, mood: FishMood, bob: Float, tilt: Float) {
    val cyan = Look.Sea
    val deep = Look.SeaDeep
    val belly = Look.SeaBelly

    rotate(degrees = tilt, pivot = Offset(s * 0.5f, s * 0.5f + bob)) {
        // 尾巴（分叉）
        val tail = Path().apply {
            moveTo(s * 0.78f, s * 0.50f + bob)
            cubicTo(s * 0.90f, s * 0.34f + bob, s * 1.00f, s * 0.30f + bob, s * 0.97f, s * 0.50f + bob)
            cubicTo(s * 1.00f, s * 0.70f + bob, s * 0.90f, s * 0.66f + bob, s * 0.78f, s * 0.50f + bob)
            close()
        }
        drawPath(tail, color = deep)

        // 背鳍
        val fin = Path().apply {
            moveTo(s * 0.44f, s * 0.27f + bob)
            cubicTo(s * 0.52f, s * 0.13f + bob, s * 0.64f, s * 0.16f + bob, s * 0.66f, s * 0.30f + bob)
            close()
        }
        drawPath(fin, color = deep.copy(alpha = 0.85f))

        // 身体（肥一点才有"肥鱼"的样子）
        drawOval(
            color = cyan,
            topLeft = Offset(s * 0.10f, s * 0.22f + bob),
            size = Size(s * 0.74f, s * 0.54f)
        )

        // 肚子（浅色，压出体积感）
        drawOval(
            color = belly,
            topLeft = Offset(s * 0.18f, s * 0.44f + bob),
            size = Size(s * 0.52f, s * 0.26f)
        )

        // 侧鳍
        val side = Path().apply {
            moveTo(s * 0.40f, s * 0.56f + bob)
            cubicTo(s * 0.44f, s * 0.70f + bob, s * 0.54f, s * 0.72f + bob, s * 0.56f, s * 0.58f + bob)
            close()
        }
        drawPath(side, color = deep.copy(alpha = 0.55f))

        // 眼睛：睁得大是担心，眯起来是开心
        val eyeC = Offset(s * 0.30f, s * 0.42f + bob)
        when (mood) {
            FishMood.HAPPY -> {
                // 弯弯的笑眼
                val smileEye = Path().apply {
                    moveTo(eyeC.x - s * 0.045f, eyeC.y + s * 0.012f)
                    quadraticBezierTo(eyeC.x, eyeC.y - s * 0.040f, eyeC.x + s * 0.045f, eyeC.y + s * 0.012f)
                }
                drawPath(smileEye, color = Look.Ink, style = Stroke(width = s * 0.028f))
            }

            else -> {
                drawCircle(Color.White, radius = s * 0.062f, center = eyeC)
                val pupilOffset = if (mood == FishMood.WORRIED) Offset(-s * 0.012f, s * 0.004f) else Offset(0f, 0f)
                drawCircle(Look.Ink, radius = s * 0.034f, center = eyeC + pupilOffset)
                drawCircle(Color.White, radius = s * 0.012f, center = eyeC + pupilOffset + Offset(-s * 0.010f, -s * 0.012f))
            }
        }

        // 腮红：担心的时候更明显一点
        drawCircle(
            color = Look.Clay.copy(alpha = if (mood == FishMood.WORRIED) 0.26f else 0.16f),
            radius = s * 0.045f,
            center = Offset(s * 0.40f, s * 0.56f + bob)
        )

        // 嘴：担心是往下撇，其余是小小的上翘
        val mouth = Path().apply {
            if (mood == FishMood.WORRIED) {
                moveTo(s * 0.20f, s * 0.56f + bob)
                quadraticBezierTo(s * 0.245f, s * 0.505f + bob, s * 0.29f, s * 0.56f + bob)
            } else {
                moveTo(s * 0.20f, s * 0.52f + bob)
                quadraticBezierTo(s * 0.245f, s * 0.565f + bob, s * 0.29f, s * 0.52f + bob)
            }
        }
        drawPath(mouth, color = Look.Ink.copy(alpha = 0.75f), style = Stroke(width = s * 0.022f))

        // 担心的时候，头顶冒出两滴小汗
        if (mood == FishMood.WORRIED) {
            drawCircle(cyan.copy(alpha = 0.55f), radius = s * 0.030f, center = Offset(s * 0.72f, s * 0.18f + bob))
            drawCircle(cyan.copy(alpha = 0.35f), radius = s * 0.020f, center = Offset(s * 0.80f, s * 0.10f + bob))
        }

        // 开心的时候冒两个泡泡
        if (mood == FishMood.HAPPY) {
            drawCircle(cyan.copy(alpha = 0.35f), radius = s * 0.026f, center = Offset(s * 0.78f, s * 0.16f + bob))
            drawCircle(cyan.copy(alpha = 0.22f), radius = s * 0.017f, center = Offset(s * 0.86f, s * 0.08f + bob))
        }
    }
}