package com.mybus.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.BookingData
import com.mybus.app.data.remote.dto.BusDetailData
import com.mybus.app.data.repository.BusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminBusDetailUiState(
    val isLoading: Boolean = false,
    val bus: BusDetailData? = null,
    val bookings: List<BookingData> = emptyList(),
    val bookingsLoading: Boolean = false,
    val error: String? = null,
    val actionInProgress: String? = null
)

@HiltViewModel
class AdminBusDetailViewModel @Inject constructor(
    private val busRepository: BusRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val busId: String = savedStateHandle["busId"] ?: ""

    private val _uiState = MutableStateFlow(AdminBusDetailUiState())
    val uiState: StateFlow<AdminBusDetailUiState> = _uiState

    init {
        loadAll()
    }

    fun loadAll() {
        loadBusDetail()
        loadBookings()
    }

    private fun loadBusDetail() {
        if (busId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            busRepository.getBusDetailWithReturnTimes(busId)
                .onSuccess { bus ->
                    _uiState.value = _uiState.value.copy(isLoading = false, bus = bus)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load bus"
                    )
                }
        }
    }

    fun loadBookings() {
        if (busId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(bookingsLoading = true)
            busRepository.getAdminBookingsForBus(busId)
                .onSuccess { bookings ->
                    _uiState.value = _uiState.value.copy(
                        bookingsLoading = false,
                        bookings = bookings
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(bookingsLoading = false)
                }
        }
    }

    fun approveBooking(bookingId: String) {
        updateStatus(bookingId, "approve")
    }

    fun rejectBooking(bookingId: String) {
        updateStatus(bookingId, "reject")
    }

    fun approveCancellation(bookingId: String) {
        updateStatus(bookingId, "approve_cancellation")
    }

    fun rejectCancellation(bookingId: String) {
        updateStatus(bookingId, "reject_cancellation")
    }

    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = bookingId)
            busRepository.cancelBookingAsAdmin(bookingId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(actionInProgress = null)
                    loadAll()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(actionInProgress = null)
                }
        }
    }

    private fun updateStatus(bookingId: String, action: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = bookingId)
            busRepository.updateBookingStatus(bookingId, action)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(actionInProgress = null)
                    loadAll()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(actionInProgress = null)
                }
        }
    }
}
