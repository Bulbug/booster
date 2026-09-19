package com.gameboostx.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gameboostx.app.data.OverlayPermissionHelper
import com.gameboostx.app.data.model.ProfileType
import com.gameboostx.app.overlay.OverlayController
import com.gameboostx.app.session.GamingSessionManager
import com.gameboostx.app.session.SessionUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SessionViewModel(
    private val sessionManager: GamingSessionManager,
    private val overlayController: OverlayController,
) : ViewModel() {

    val uiState: StateFlow<SessionUiState> = sessionManager.state

    init {
        sessionManager.state.onEach { state ->
            if (overlayController.isShowing) overlayController.update(state)
        }.launchIn(viewModelScope)
    }

    fun start(packageName: String, displayName: String, profile: ProfileType) {
        sessionManager.startSession(packageName, displayName, profile)
    }

    fun endSession(reason: String = "manual") {
        viewModelScope.launch { sessionManager.endSession(reason) }
    }

    fun dismissExitDetected() = sessionManager.dismissExitDetected()

    fun overlayGranted(context: android.content.Context) = OverlayPermissionHelper.isGranted(context)

    fun toggleOverlay(context: android.content.Context): Boolean {
        return if (overlayController.isShowing) {
            overlayController.hide()
            false
        } else {
            val shown = overlayController.show()
            if (shown) overlayController.update(uiState.value)
            shown
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Overlay intentionally outlives this ViewModel (screen navigation shouldn't hide it) —
        // it's torn down by GameBoostApplication when the session itself ends.
    }
}
