package com.durgamma.festival

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.durgamma.festival.core.theme.DurgammaTheme
import com.durgamma.festival.presentation.navigation.DurgammaNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DurgammaTheme {
                DurgammaNavGraph()
            }
        }
    }
}
