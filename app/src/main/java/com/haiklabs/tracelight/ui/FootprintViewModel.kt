package com.haiklabs.tracelight.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haiklabs.tracelight.SearchProfile
import com.haiklabs.tracelight.SearchProfileValidator
import com.haiklabs.tracelight.repo.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { Search, CandidateProgress, Candidates, NeedsMoreInfo, Preview, Payment, ReportProgress, Report }

data class FootprintUiState(
    val screen: Screen = Screen.Search,
    val profile: SearchProfile = SearchProfile(),
    val error: String? = null,
    val searchResponse: CandidateSearchResponse? = null,
    val selectedCandidate: PersonCandidate? = null,
    val report: DeepSearchReport? = null
)

class FootprintViewModel(private val repository: FootprintRepository = FirebaseFootprintRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(FootprintUiState())
    val uiState: StateFlow<FootprintUiState> = _uiState
    private var job: Job? = null

    fun updateProfile(profile: SearchProfile) = _uiState.update { it.copy(profile = profile, error = null) }

    fun startCandidateSearch() {
        val profile = _uiState.value.profile
        SearchProfileValidator.validate(profile)?.let { message ->
            _uiState.update { it.copy(error = message) }; return
        }
        job?.cancel()
        _uiState.update { it.copy(screen = Screen.CandidateProgress, error = null, searchResponse = null) }
        job = viewModelScope.launch {
            repository.findCandidates(profile).onSuccess { result ->
                val screen = when (result.status) {
                    SearchStatus.CANDIDATES_FOUND -> if (result.candidates.isEmpty()) Screen.NeedsMoreInfo else Screen.Candidates
                    SearchStatus.NEEDS_MORE_INFO, SearchStatus.NO_RELIABLE_MATCH -> Screen.NeedsMoreInfo
                }
                _uiState.update { it.copy(screen = screen, searchResponse = result) }
            }.onFailure(::showSearchError)
        }
    }

    fun selectCandidate(candidate: PersonCandidate) = _uiState.update {
        it.copy(screen = Screen.Preview, selectedCandidate = candidate)
    }

    fun openPayment() = _uiState.update { it.copy(screen = Screen.Payment) }

    /** Called after Play Billing confirms the one-time product. The current UI button is a test unlock. */
    fun confirmPurchaseAndGenerate() {
        val candidate = _uiState.value.selectedCandidate ?: return
        val profile = _uiState.value.profile
        job?.cancel()
        _uiState.update { it.copy(screen = Screen.ReportProgress, error = null) }
        job = viewModelScope.launch {
            repository.createReport(profile, candidate).onSuccess { report ->
                _uiState.update { it.copy(screen = Screen.Report, report = report) }
            }.onFailure { error ->
                _uiState.update { it.copy(screen = Screen.Payment, error = friendlyError(error)) }
            }
        }
    }

    fun editSearch() { job?.cancel(); _uiState.update { it.copy(screen = Screen.Search, error = null) } }
    fun backToPreview() = _uiState.update { it.copy(screen = Screen.Preview, error = null) }
    fun deleteReport() = _uiState.update { FootprintUiState() }
    fun retryWithMoreInfo() = _uiState.update { it.copy(screen = Screen.Search, error = null) }

    private fun showSearchError(error: Throwable) = _uiState.update {
        it.copy(screen = Screen.Search, error = friendlyError(error))
    }

    private fun friendlyError(error: Throwable): String = error.message?.takeIf(String::isNotBlank)
        ?: "We couldn't complete the search. Check your connection and try again."
}
