package com.google.adk.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import com.google.adk.demo.navigation.AdkNavHost
import com.google.adk.demo.ui.theme.AdkDemoTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // WindowSizeClass drives the adaptive layout: compact width keeps
            // the phone layout, medium/expanded (car landscape) gets the rail
            // layout + automotive dark theme.
            val windowSizeClass = calculateWindowSizeClass(this)
            AdkDemoTheme(windowWidthSizeClass = windowSizeClass.widthSizeClass) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AdkNavHost(windowWidthSizeClass = windowSizeClass.widthSizeClass)
                }
            }
        }
    }
}
