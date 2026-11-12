package com.mybus.app.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.UnifiedBookingItem
import com.mybus.app.data.repository.BusRepository
import com.mybus.app.data.repository.StayRepository
import com.mybus.app.data.repository.UnifiedBookingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnifiedBookingDetailUiState(
    val isLoading: Boolean = false,
    val booking: UnifiedBookingItem? = null,
    val error: String? = null,
    val message: String? = null,
    val isCancelling: Boolean = false
)

@HiltViewModel
class UnifiedBookingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: UnifiedBookingRepository,
    private val busRepository: BusRepository,
    private val stayRepository: StayRepository
) : ViewModel() {
    private val bookingType: String = savedStateHandle["bookingType"] ?: ""
    private val bookingId: String = savedStateHandle["bookingId"] ?: ""
    private val _uiState = MutableStateFlow(UnifiedBookingDetailUiState())
    val uiState: StateFlow<UnifiedBookingDetailUiState> = _uiState

    init {
        load()
    }

    fun load() {
        if (bookingType.isBlank() || bookingId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getBooking(bookingType, bookingId)
                .onSuccess { booking ->
                    _uiState.value = _uiState.value.copy(isLoading = false, booking = booking)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load booking details"
                    )
                }
        }
    }

    /** Runs once at a time; [onFinished] fires when the server answers, either way. */
    fun requestCancellation(reason: String, onFinished: () -> Unit) {
        val booking = _uiState.value.booking ?: return
        if (_uiState.value.isCancelling) return
        _uiState.value = _uiState.value.copy(isCancelling = true, error = null, message = null)
        viewModelScope.launch {
            val result = when (booking.bookingType) {
                // Only Stay takes a reason; the Bus request has none.
                "bus" -> busRepository.requestBookingCancellation(booking.bookingId).map { Unit }
                "stay" -> stayRepository.requestCancellation(
                    booking.bookingId,
                    reason.trim().ifEmpty { null }
                ).map { Unit }
                else -> Result.failure(Exception("Cancellation is not available for this booking"))
            }
            onFinished()
            result.onSuccess {
                val wasCancelledImmediately = booking.bookingType == "stay" &&
                    booking.rawStatus == "pending"
                _uiState.value = _uiState.value.copy(
                    isCancelling = false,
                    message = if (wasCancelledImmediately) {
                        "Your booking has been cancelled"
                    } else {
                        "Cancellation request submitted"
                    }
                )
                load()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isCancelling = false,
                    error = error.message ?: "Failed to request cancellation"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
