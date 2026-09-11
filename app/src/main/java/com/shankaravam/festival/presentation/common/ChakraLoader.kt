package com.shankaravam.festival.presentation.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shankaravam.festival.R
import com.shankaravam.festival.core.theme.TempleGold

/**
 * Rotating Vishnu Sudarshana Chakra with radiant aura.
 * Per jetpack-compose-performance skill: 3000ms linear spin, GPU rotationZ layer.
 */
@Composable
fun VishnuChakraLoader(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    tint: Color = TempleGold
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ChakraSpin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "Angle"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(TempleGold.copy(alpha = 0.35f), Color.Transparent)
                )
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_sudarshana_chakra),
            contentDescription = "Sudarshana Chakra",
            tint = tint,
            modifier = Modifier
                .fillMaxSize(0.75f)
                .graphicsLayer { rotationZ = angle }
        )
    }
}
