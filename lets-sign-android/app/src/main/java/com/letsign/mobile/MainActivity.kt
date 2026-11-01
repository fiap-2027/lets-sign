package com.letsign.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.letsign.mobile.presentation.navigation.AppNavHost
import com.letsign.mobile.presentation.theme.EasySignTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            EasySignTheme {
                AppNavHost()
            }
        }
    }
}
