package com.durgamma.festival.presentation.common

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.durgamma.festival.core.theme.DeepMaroon
import com.durgamma.festival.core.theme.TempleGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempleAppBar(
    title: String,
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = { Text(title) },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = DeepMaroon,
            titleContentColor = TempleGold
        )
    )
}
