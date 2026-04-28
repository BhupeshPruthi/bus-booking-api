package com.mybus.app.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.BookingData
import com.mybus.app.data.repository.BusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookingDetailUiState(
    val isLoading: Boolean = false,
    val booking: BookingData? = null,
    val error: String? = null,
    val isCancellationInProgress: Boolean = false,
    val actionMessage: String? = null,
    val isActionError: Boolean = false
)

@HiltViewModel
class BookingDetailViewModel @Inject constructor(
    private val busRepository: BusRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookingId: String = savedStateHandle["bookingId"] ?: ""

    private val _uiState = MutableStateFlow(BookingDetailUiState())
    val uiState: StateFlow<BookingDetailUiState> = _uiState

    init {
        loadBookingDetail()
    }

    fun loadBookingDetail() {
        if (bookingId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                actionMessage = null,
                isActionError = false
            )
            busRepository.getBookingById(bookingId)
                .onSuccess { booking ->
                    _uiState.value = _uiState.value.copy(isLoading = false, booking = booking)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load booking"
                    )
                }
        }
    }

    fun requestCancellation() {
        if (bookingId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCancellationInProgress = true,
                actionMessage = null,
                isActionError = false
            )
            busRepository.requestBookingCancellation(bookingId)
                .onSuccess { booking ->
                    val message = if (booking.status == "cancelled") {
                        "Booking cancelled"
                    } else {
                        "Cancellation request sent"
                    }
                    _uiState.value = _uiState.value.copy(
                        isCancellationInProgress = false,
                        booking = booking,
                        actionMessage = message,
                        isActionError = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isCancellationInProgress = false,
                        actionMessage = e.message ?: "Failed to request cancellation",
                        isActionError = true
                    )
                }
        }
    }
}
