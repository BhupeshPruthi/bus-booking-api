package com.mybus.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.EventListItem
import com.mybus.app.data.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class AddEventUiState(
    val header: String = "",
    val subHeader: String = "",
    val date: LocalDate? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val created: EventListItem? = null
)

@HiltViewModel
class AddEventViewModel @Inject constructor(
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEventUiState())
    val uiState: StateFlow<AddEventUiState> = _uiState

    fun updateHeader(value: String) {
        _uiState.value = _uiState.value.copy(header = value.take(200), error = null)
    }

    fun updateSubHeader(value: String) {
        _uiState.value = _uiState.value.copy(subHeader = value.take(500), error = null)
    }

    fun updateDate(value: LocalDate) {
        _uiState.value = _uiState.value.copy(date = value, error = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissSuccess() {
        _uiState.value = AddEventUiState()
    }

    fun createEvent() {
        val state = _uiState.value
        val header = state.header.trim()
        val subHeader = state.subHeader.trim()
        val date = state.date

        if (header.isBlank()) {
            _uiState.value = state.copy(error = "Please enter a header")
            return
        }
        if (subHeader.isBlank()) {
            _uiState.value = state.copy(error = "Please enter a sub header")
            return
        }
        if (date == null) {
            _uiState.value = state.copy(error = "Please select an event date")
            return
        }

        // Treat event as an all-day date. Use end-of-day in local zone so "today" still works.
        val eventInstant = date
            .atTime(LocalTime.of(23, 59))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toString()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            eventRepository.createEvent(header = header, subHeader = subHeader, eventDate = eventInstant)
                .onSuccess { created ->
                    _uiState.value = _uiState.value.copy(isLoading = false, created = created)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create event"
                    )
                }
        }
    }
}

