package com.mybus.app.ui.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.FeedbackItem
import com.mybus.app.data.remote.dto.HelpContact
import com.mybus.app.data.repository.SupportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SupportUiState(
    val helpContacts: List<HelpContact> = emptyList(),
    val isLoadingHelp: Boolean = false,
    val helpError: String? = null,
    val message: String = "",
    val isSubmitting: Boolean = false,
    val submissionComplete: Boolean = false,
    val isLoadingFeedback: Boolean = false,
    val feedback: List<FeedbackItem> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val submissionError: String? = null,
    val feedbackError: String? = null
)

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val repository: SupportRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SupportUiState())
    val uiState: StateFlow<SupportUiState> = _uiState

    init {
        loadHelpContacts()
    }

    fun loadHelpContacts() {
        if (_uiState.value.isLoadingHelp) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHelp = true, helpError = null)
            repository.getHelpContacts()
                .onSuccess { contacts ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingHelp = false,
                        helpContacts = contacts
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingHelp = false,
                        helpError = error.message ?: "Failed to load Help contacts."
                    )
                }
        }
    }

    fun updateMessage(message: String) {
        if (message.length <= 2000) {
            _uiState.value = _uiState.value.copy(
                message = message,
                submissionComplete = false,
                submissionError = null
            )
        }
    }

    fun clearSubmissionConfirmation() {
        if (_uiState.value.submissionComplete) {
            _uiState.value = _uiState.value.copy(submissionComplete = false)
        }
    }

    fun submitFeedback() {
        val message = _uiState.value.message.trim()
        if (message.isBlank()) {
            _uiState.value = _uiState.value.copy(submissionError = "Please enter your feedback.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, submissionError = null)
            repository.submitFeedback(message)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        message = "",
                        isSubmitting = false,
                        submissionComplete = true
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        submissionError = error.message ?: "Failed to send feedback."
                    )
                }
        }
    }

    fun loadFeedback(reset: Boolean = true) {
        if (_uiState.value.isLoadingFeedback) return
        val requestedPage = if (reset) 1 else _uiState.value.page + 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFeedback = true, feedbackError = null)
            repository.getFeedback(requestedPage)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingFeedback = false,
                        feedback = if (reset) result.items else _uiState.value.feedback + result.items,
                        page = result.page,
                        total = result.total
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingFeedback = false,
                        feedbackError = error.message ?: "Failed to load feedback."
                    )
                }
        }
    }
}
