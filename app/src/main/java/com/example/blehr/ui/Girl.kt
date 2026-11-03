package com.example.blehr.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.blehr.R
import com.example.blehr.logic.MascotMood

/** AI 再创作的三态鲸鱼娘头像；来源与生成记录见 assets/visuals-v024。 */
@Composable
fun GirlMascot(
    mood: MascotMood = MascotMood.IDLE,
    size: Dp = 72.dp,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val t: Float = if (animated) {
        val transition = rememberInfiniteTransition(label = "girl")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(if (mood == MascotMood.WORRIED) 2600 else 3600),
                RepeatMode.Reverse
            ),
            label = "bob"
        )
        value
    } else 0.5f
    val artwork = when (mood) {
        MascotMood.HAPPY -> R.drawable.art_v024_xiaoye_happy
        MascotMood.IDLE -> R.drawable.art_v024_xiaoye_idle
        MascotMood.WORRIED -> R.drawable.art_v024_xiaoye_worried
    }
    Image(
        painter = painterResource(artwork),
        contentDescription = null, // 名字与状态已由相邻文字说明。
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size).graphicsLayer {
            translationY = (t - 0.5f) * size.toPx() * 0.025f
            rotationZ = (t - 0.5f) * 2f
        }
    )
}
