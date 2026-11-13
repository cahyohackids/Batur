package com.cahyo.batur

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cahyo.batur.ui.navigation.BaturApp
import com.cahyo.batur.ui.theme.BaturTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BaturApplication).container
        setContent {
            BaturTheme {
                BaturApp(container)
            }
        }
    }
}
