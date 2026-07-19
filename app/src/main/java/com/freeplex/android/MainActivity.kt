package com.freeplex.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.freeplex.android.core.designsystem.FreePlexTheme
import com.freeplex.android.navigation.FreePlexNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreePlexTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FreePlexNavHost()
                }
            }
        }
    }
}
