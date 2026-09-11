package com.shankaravam.festival

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.shankaravam.festival.core.i18n.EnglishStrings
import com.shankaravam.festival.core.i18n.TeluguStrings
import com.shankaravam.festival.core.theme.ShankaRavamTheme
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.presentation.navigation.ShankaRavamNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ShankaRavamApp
        setContent {
            val languageCode by app.container.sessionPrefs.appLanguage.collectAsState()
            val strings = if (languageCode == SessionPrefs.LANG_TELUGU) TeluguStrings else EnglishStrings
            ShankaRavamTheme(strings = strings) {
                ShankaRavamNavGraph()
            }
        }
    }
}
