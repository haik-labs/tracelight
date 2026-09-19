package com.haiklabs.tracelight.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haiklabs.tracelight.SearchProfile
import com.haiklabs.tracelight.SearchProfileValidator
import com.haiklabs.tracelight.repo.FirebaseFootprintRepository
import com.haiklabs.tracelight.repo.FootprintReport
import com.haiklabs.tracelight.repo.FootprintRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { Home, Scanning, Preview, Report }

data class FootprintUiState(
    val screen: Screen = Screen.Home,
    val profile: SearchProfile = SearchProfile("", "", ""),
    /** Validation or scan error shown on the Home screen. */
    val error: String? = null,
    val report: FootprintReport? = null
)

class FootprintViewModel(
    private val repository: FootprintRepository = FirebaseFootprintRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FootprintUiState())
    val uiState: StateFlow<FootprintUiState> = _uiState

    private var scanJob: Job? = null

    fun updateProfile(profile: SearchProfile) {
        _uiState.update { it.copy(profile = profile, error = null) }
    }

    fun startScan() {
        val profile = _uiState.value.profile
        val validationError = SearchProfileValidator.validate(profile)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }
        _uiState.update { it.copy(screen = Screen.Scanning, error = null, report = null) }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            repository.scan(profile)
                .onSuccess { report ->
                    _uiState.update { it.copy(screen = Screen.Preview, report = report) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            screen = Screen.Home,
                            error = e.message?.takeIf(String::isNotBlank)
                                ?: "We couldn't complete the scan. Please try again."
                        )
                    }
                }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(screen = Screen.Home) }
    }

    fun unlockReport() {
        _uiState.update { it.copy(screen = Screen.Report) }
    }

    fun editDetails() {
        _uiState.update { it.copy(screen = Screen.Home, error = null) }
    }

    /** Returns to Home and discards the generated report. */
    fun finishAndDelete() {
        _uiState.update { it.copy(screen = Screen.Home, report = null, error = null) }
    }
}
