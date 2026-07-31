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
    val message: String? = null
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

    fun requestCancellation(reason: String?) {
        val booking = _uiState.value.booking ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)
            val result = when (booking.bookingType) {
                "bus" -> busRepository.requestBookingCancellation(booking.bookingId).map { Unit }
                "stay" -> stayRepository.requestCancellation(booking.bookingId, reason).map { Unit }
                else -> Result.failure(Exception("Cancellation is not available for this booking"))
            }
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Cancellation request submitted"
                )
                load()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Failed to request cancellation"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
