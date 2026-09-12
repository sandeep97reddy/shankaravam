package com.shankaravam.festival.presentation.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shankaravam.festival.R
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.SacredCharcoal
import com.shankaravam.festival.core.theme.TempleGold
import com.shankaravam.festival.presentation.common.VishnuChakraLoader
import kotlinx.coroutines.delay

/** Cold-launch budget: 1400ms (within mandatory 1.2–1.5s window). */
const val SPLASH_TIMEOUT_MS = 1400L

@Composable
fun SplashScreen(
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        delay(SPLASH_TIMEOUT_MS)
        onTimeout()
    }
    // Staggered title entrance: rises gently after the emblem lights up.
    var titleVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250)
        titleVisible = true
    }
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "SplashTitleFade"
    )
    val titleRise by animateFloatAsState(
        targetValue = if (titleVisible) 0f else 14f,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "SplashTitleRise"
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepMaroon, SacredCharcoal)
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        VishnuChakraLoader(size = 128.dp)
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TempleGold,
            modifier = Modifier
                .alpha(titleAlpha)
                .offset(y = titleRise.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "ఉత్సవ నిర్వహణ",
            style = MaterialTheme.typography.bodyLarge,
            color = TempleGold.copy(alpha = 0.8f),
            modifier = Modifier.alpha(titleAlpha)
        )
        Spacer(Modifier.height(10.dp))
        // Thin gold divider that fades in with the title — quiet, temple-like.
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth(0.22f)
                .height(2.dp)
                .alpha(titleAlpha)
        ) {
            drawLine(
                color = TempleGold.copy(alpha = 0.6f),
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
