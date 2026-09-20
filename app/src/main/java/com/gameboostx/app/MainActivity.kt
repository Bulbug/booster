package com.gameboostx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gameboostx.app.ui.navigation.GameBoostNavHost
import com.gameboostx.app.ui.onboarding.OnboardingScreen
import com.gameboostx.app.ui.onboarding.OnboardingViewModel
import com.gameboostx.app.ui.theme.GameBoostXTheme
import com.gameboostx.app.viewmodel.GameBoostViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameBoostXTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(application as GameBoostApplication)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(app: GameBoostApplication) {
    // completed starts false and the wizard's finish() writes true to the same DataStore, so
    // this recomposes into the main nav host automatically — no separate local "done" flag needed.
    val onboardingCompleted by app.onboardingStore.completed.collectAsState(initial = null)

    when (onboardingCompleted) {
        null -> Unit // brief gap before the first DataStore read completes — nothing to show yet
        false -> {
            val vm: OnboardingViewModel = viewModel(factory = GameBoostViewModelFactory(app))
            // Same tablet/large-screen width cap as the main nav host, for consistency.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                OnboardingScreen(viewModel = vm, onFinished = {}, modifier = Modifier.widthIn(max = 640.dp).fillMaxSize())
            }
        }
        true -> GameBoostNavHost(app = app)
    }
}
