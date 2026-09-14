package com.shankaravam.festival

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.shankaravam.festival.core.i18n.EnglishStrings
import com.shankaravam.festival.core.i18n.TeluguStrings
import com.shankaravam.festival.core.theme.ShankaRavamTheme
import com.shankaravam.festival.core.ui.haptics.LocalAppHaptics
import com.shankaravam.festival.core.ui.haptics.rememberAppHaptics
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.presentation.navigation.ShankaRavamNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ShankaRavamApp
        setContent {
            val languageCode by app.container.sessionPrefs.appLanguage.collectAsState()
            val hapticEnabled by app.container.sessionPrefs.hapticFeedbackEnabled.collectAsState()
            val strings = if (languageCode == SessionPrefs.LANG_TELUGU) TeluguStrings else EnglishStrings
            val appHaptics = rememberAppHaptics(enabled = hapticEnabled)

            ShankaRavamTheme(strings = strings) {
                CompositionLocalProvider(LocalAppHaptics provides appHaptics) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ShankaRavamNavGraph()
                    }
                }
            }
        }
    }
}
