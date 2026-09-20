package com.gameboostx.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Special permissions like Overlay and Usage Access have no runtime-permission callback — the
 * only way to notice a grant is to re-check when the user comes back from the Settings screen
 * they were sent to. Without this, Settings/Session screens showed a stale "Not granted" until
 * some unrelated recomposition happened to fire.
 */
@Composable
fun rememberResumePermissionState(check: () -> Boolean): Boolean {
    var granted by remember { mutableStateOf(check()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = check()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return granted
}
