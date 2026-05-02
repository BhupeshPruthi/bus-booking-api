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
import java.time.Instant
import javax.inject.Inject

data class AdminBusDetailUiState(
    val isLoading: Boolean = false,
    val bus: BusDetailData? = null,
    val bookings: List<BookingData> = emptyList(),
    val bookingsLoading: Boolean = false,
    val bookingsError: String? = null,
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
        loadBusDetail(refreshBookings = true)
    }

    private fun loadBusDetail(refreshBookings: Boolean = false) {
        if (busId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            busRepository.getBusDetailWithReturnTimes(busId)
                .onSuccess { bus ->
                    _uiState.value = _uiState.value.copy(isLoading = false, bus = bus)
                    if (refreshBookings) {
                        loadBookings(bus)
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load bus"
                    )
                }
        }
    }

    fun loadBookings(bus: BusDetailData? = _uiState.value.bus) {
        if (busId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(bookingsLoading = true, bookingsError = null)

            val primaryResult = busRepository.getAdminBookingsForBus(busId)
            val primaryBookings = primaryResult.getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    bookingsLoading = false,
                    bookingsError = error.message ?: "Failed to load bookings"
                )
                return@launch
            }

            val returnBusId = bus?.returnBusId
                ?.takeIf { bus.tripType == "round_trip" && it.isNotBlank() && it != busId }

            val combinedBookings = if (returnBusId != null) {
                val returnResult = busRepository.getAdminBookingsForBus(returnBusId)
                val returnBookings = returnResult.getOrElse { error ->
                    _uiState.value = _uiState.value.copy(
                        bookingsLoading = false,
                        bookings = primaryBookings.sortedForAdminReview(),
                        bookingsError = error.message ?: "Failed to load return trip bookings"
                    )
                    return@launch
                }
                (primaryBookings + returnBookings)
            } else {
                primaryBookings
            }

            _uiState.value = _uiState.value.copy(
                bookingsLoading = false,
                bookings = combinedBookings
                    .distinctBy { it.id }
                    .sortedForAdminReview(),
                bookingsError = null
            )
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

    private fun List<BookingData>.sortedForAdminReview(): List<BookingData> {
        val statusRank = mapOf(
            "pending" to 0,
            "payment_uploaded" to 1,
            "cancellation_requested" to 2,
            "confirmed" to 3,
            "rejected" to 4,
            "cancelled" to 5,
            "expired" to 6
        )
        return sortedWith(
            compareBy<BookingData> { statusRank[it.status] ?: 99 }
                .thenByDescending { booking ->
                    booking.createdAt
                        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        ?: Instant.EPOCH
                }
        )
    }
}
