package com.shankaravam.festival.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shankaravam.festival.R
import com.shankaravam.festival.core.i18n.appStrings
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.SaffronWash
import com.shankaravam.festival.core.theme.TempleGold
import com.shankaravam.festival.core.theme.TempleSaffron

/**
 * Top brand header on the opening dashboard:
 * - App Name "ShankaRavam" / "శంఖారావం" at the top-left with minted Sudarshana Chakra medallion
 * - Two-tone brand typography lockup with heritage bilingual micro-pill
 * - Editorial tracked subtitle
 * - Refined circular quick settings action
 * - Overflow-proof flex layout supporting all screen widths (280dp-1200dp) and accessibility font scales
 */
@Composable
fun ShankaRavamDashboardHeader(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val isDark = isSystemInDarkTheme()
    val isTelugu = strings.languageCode == "te"

    // Harmonious contrast tokens: Deep Maroon for heritage base, Temple Saffron for sacred warmth
    val brandPrimary = if (isDark) Color(0xFFFFD5BD) else DeepMaroon
    val brandAccent = if (isDark) Color(0xFFFFAB7B) else TempleSaffron
    val badgeBorder = if (isDark) TempleGold.copy(alpha = 0.35f) else TempleGold.copy(alpha = 0.50f)
    val badgeBg = if (isDark) TempleGold.copy(alpha = 0.12f) else TempleGold.copy(alpha = 0.16f)
    val badgeText = if (isDark) TempleGold else DeepMaroon.copy(alpha = 0.85f)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Logo & Title on the Top Left (weighted to flex safely without pushing Settings off)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Minted Sacred Emblem Medallion (fixed 42dp anchor)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDark) {
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF382316),
                                        Color(0xFF1E1610)
                                    )
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFFFDF7),
                                        Color(0xFFFEF3C7).copy(alpha = 0.55f),
                                        Color(0xFFFFEDD5).copy(alpha = 0.35f)
                                    )
                                )
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) TempleGold.copy(alpha = 0.35f) else TempleGold.copy(alpha = 0.60f),
                            shape = CircleShape
                        )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_sudarshana_chakra),
                        contentDescription = "ShankaRavam Emblem",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Brand Typography Lockup (weighted to handle high accessibility font scaling)
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isTelugu) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            color = brandPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    ) {
                                        append("శంఖా")
                                    }
                                    withStyle(
                                        SpanStyle(
                                            color = brandAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    ) {
                                        append("రావం")
                                    }
                                },
                                modifier = Modifier.weight(1f, fill = false),
                                fontSize = 19.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Cultural secondary micro-badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeBg)
                                    .border(
                                        width = 0.75.dp,
                                        color = badgeBorder,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "SHANKARAVAM",
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = badgeText,
                                    maxLines = 1
                                )
                            }
                        } else {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            color = brandPrimary,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    ) {
                                        append("Shanka")
                                    }
                                    withStyle(
                                        SpanStyle(
                                            color = brandAccent,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    ) {
                                        append("Ravam")
                                    }
                                },
                                modifier = Modifier.weight(1f, fill = false),
                                fontSize = 19.sp,
                                letterSpacing = (-0.2).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Telugu script micro-badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeBg)
                                    .border(
                                        width = 0.75.dp,
                                        color = badgeBorder,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "శంఖారావం",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = badgeText,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Refined Subtitle
                    Text(
                        text = if (isTelugu) strings.appSubtitle else strings.appSubtitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = if (isTelugu) 10.sp else 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = if (isTelugu) 0.2.sp else 1.1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quick Actions on the Top Right (Settings) - guaranteed fixed 40dp width
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF2C2018) else Color(0xFFFAF7F2))
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color(0xFF4D3B2E) else Color(0xFFE8DFD3),
                        shape = CircleShape
                    )
            ) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = if (isDark) Color(0xFFFFD5BD) else DeepMaroon,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/**
 * Standard app bar for child screens with back arrow and customizable actions.
 * Light, roomy surface with crisp onSurface typography and Saffron accents.
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
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TempleSaffron
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = TempleSaffron,
            actionIconContentColor = TempleSaffron
        )
    )
}
