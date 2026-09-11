package com.shankaravam.festival.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shankaravam.festival.R
import com.shankaravam.festival.core.i18n.appStrings
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.TempleGold
import com.shankaravam.festival.core.theme.TempleSaffron

/**
 * Top brand header on the opening dashboard:
 * - App Name "ShankaRavam" / "శంఖారావం" at the top-left with divine Sudarshana Chakra emblem
 * - Subtitle
 * - Quick 1-tap Language Switcher toggle chip (EN | తె)
 * - Quick settings action
 */
@Composable
fun ShankaRavamDashboardHeader(
    onToggleLanguage: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Surface(
        color = DeepMaroon,
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Logo & Title on the Top Left
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(TempleGold.copy(alpha = 0.15f))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_sudarshana_chakra),
                        contentDescription = "ShankaRavam Logo",
                        modifier = Modifier.size(30.dp)
                    )
                }
                Column {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TempleGold,
                        letterSpacing = 0.2.sp
                    )
                    Text(
                        text = strings.appSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }

            // Quick Actions on the Top Right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Language Switcher Chip (1-tap toggle between Telugu & English)
                FilterChip(
                    selected = true,
                    onClick = onToggleLanguage,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = "Switch Language",
                            tint = DeepMaroon,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = strings.switchLanguage,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = DeepMaroon
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TempleGold,
                        selectedLabelColor = DeepMaroon
                    )
                )

                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Standard app bar for child screens with back arrow and customizable actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempleAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TempleGold
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DeepMaroon,
            titleContentColor = TempleGold,
            navigationIconContentColor = TempleGold,
            actionIconContentColor = TempleGold
        )
    )
}
