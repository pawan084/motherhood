package com.aira.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aira.companion.ui.AiraApp
import com.aira.companion.ui.theme.AiraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.aira.companion.ui.theme.ThemeMode.load(this)
        enableEdgeToEdge()
        setContent {
            AiraTheme {
                AiraApp()
            }
        }
    }
}
