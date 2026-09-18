package com.gameboostx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gameboostx.app.ui.navigation.GameBoostNavHost
import com.gameboostx.app.ui.theme.GameBoostXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameBoostXTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameBoostNavHost(app = application as GameBoostApplication)
                }
            }
        }
    }
}
