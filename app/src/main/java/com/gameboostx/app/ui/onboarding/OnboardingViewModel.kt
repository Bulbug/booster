package com.gameboostx.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gameboostx.app.data.CapabilityManager
import com.gameboostx.app.data.DeviceInfoManager
import com.gameboostx.app.data.datastore.OnboardingStore
import com.gameboostx.app.data.datastore.SettingsStore
import com.gameboostx.app.data.model.DeviceSnapshot
import com.gameboostx.app.data.model.ProfileType
import com.gameboostx.app.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ONBOARDING_STEP_COUNT = 6

data class OnboardingUiState(
    val step: Int = 0,
    val shizukuBinderAvailable: Boolean = false,
    val scanning: Boolean = false,
    val deviceSnapshot: DeviceSnapshot? = null,
    val capabilities: Map<String, Boolean> = emptyMap(),
    val selectedDefaultProfile: ProfileType = ProfileType.BALANCED,
)

/**
 * Walks the six screens from spec §4: welcome, what the app can/can't do, optional permissions
 * explained (not requested — spec §4 is explicit that permissions are asked for only when a
 * feature actually needs them, not up front), Shizuku detection (read-only, no permission
 * prompt here either), a real device compatibility scan, and picking the default gaming
 * profile new games start with.
 */
class OnboardingViewModel(
    private val capabilityManager: CapabilityManager,
    private val deviceInfoManager: DeviceInfoManager,
    private val shizukuManager: ShizukuManager,
    private val settingsStore: SettingsStore,
    private val onboardingStore: OnboardingStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(shizukuBinderAvailable = shizukuManager.state.value.binderAvailable)
    }

    fun next() {
        val current = _uiState.value
        if (current.step >= ONBOARDING_STEP_COUNT - 1) return
        val newStep = current.step + 1
        _uiState.value = current.copy(step = newStep, shizukuBinderAvailable = shizukuManager.state.value.binderAvailable)
        if (newStep == SCAN_STEP && current.deviceSnapshot == null) runCompatibilityScan()
    }

    fun back() {
        val current = _uiState.value
        if (current.step <= 0) return
        _uiState.value = current.copy(step = current.step - 1)
    }

    fun selectDefaultProfile(profile: ProfileType) {
        _uiState.value = _uiState.value.copy(selectedDefaultProfile = profile)
    }

    private fun runCompatibilityScan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(scanning = true)
            val snapshot = withContext(Dispatchers.Default) { deviceInfoManager.snapshot() }
            _uiState.value = _uiState.value.copy(
                scanning = false,
                deviceSnapshot = snapshot,
                capabilities = capabilityManager.summary(),
            )
        }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsStore.setDefaultProfile(_uiState.value.selectedDefaultProfile)
            onboardingStore.markCompleted()
            onDone()
        }
    }

    companion object {
        private const val SCAN_STEP = 4
    }
}
