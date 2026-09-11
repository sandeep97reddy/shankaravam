package com.shankaravam.festival

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shankaravam.festival.core.theme.ShankaRavamTheme
import com.shankaravam.festival.presentation.navigation.ShankaRavamNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShankaRavamTheme {
                ShankaRavamNavGraph()
            }
        }
    }
}
