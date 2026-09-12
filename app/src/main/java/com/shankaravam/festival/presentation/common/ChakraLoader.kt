package com.shankaravam.festival.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shankaravam.festival.R
import com.shankaravam.festival.core.theme.TempleGold

/**
 * Sacred Vishnu Sudarshana Chakra reveal — NO rotation. The emblem enters
 * once (gentle scale + fade, like a lamp being lit) inside a static gold
 * prabhamandala ring, wrapped in a slow breathing halo. Calm, reverent,
 * and cheap: one finite entrance + one low-frequency alpha pulse.
 * NOTE: the artwork is full-color — no tint is applied (tinting would flatten
 * the fiery gold/red gradients into a monochrome silhouette).
 */
@Composable
fun VishnuChakraLoader(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // One-shot entrance: fade + settle from 86% scale, ease-out like a flame catching.
    val entranceAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "ChakraFade"
    )
    val entranceScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.86f,
        animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing),
        label = "ChakraScale"
    )
    // Slow breathing halo — a calm 2.6s glow pulse, never a spin.
    val breath by rememberInfiniteTransition(label = "ChakraBreath").animateFloat(
        initialValue = 0.20f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Halo"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        // Breathing halo.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(TempleGold.copy(alpha = breath), Color.Transparent)
                )
            )
        }
        // Static sacred ring (prabhamandala) — drawn once, never rotated.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(entranceAlpha)
        ) {
            val stroke = size.toPx() * 0.012f
            drawCircle(
                color = TempleGold.copy(alpha = 0.75f),
                radius = size.toPx() * 0.47f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
            )
            drawCircle(
                color = TempleGold.copy(alpha = 0.28f),
                radius = size.toPx() * 0.42f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke * 0.45f)
            )
        }
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.ic_sudarshana_chakra),
            contentDescription = "Sudarshana Chakra",
            modifier = Modifier
                .fillMaxSize(0.78f)
                .scale(entranceScale)
                .alpha(entranceAlpha)
        )
    }
}
