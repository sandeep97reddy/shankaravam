package com.shankaravam.festival.presentation.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shankaravam.festival.R
import com.shankaravam.festival.core.theme.DeepMaroon
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepMaroon),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        VishnuChakraLoader(size = 120.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TempleGold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "ఉత్సవ నిర్వహణ",
            style = MaterialTheme.typography.bodyLarge,
            color = TempleGold.copy(alpha = 0.8f)
        )
    }
}
