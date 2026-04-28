package com.mybus.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.dto.BookingData
import com.mybus.app.data.remote.dto.BusDetailData
import com.mybus.app.data.remote.dto.PickupPointInfo
import com.mybus.app.data.repository.BusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BusDetailUiState(
    val isLoading: Boolean = false,
    val bus: BusDetailData? = null,
    val error: String? = null,
    val seatCount: Int = 1,
    val passengerName: String = "",
    val passengerPhone: String = "",
    val selectedPickup: PickupPointInfo? = null,
    val showBookingDialog: Boolean = false,
    val isBooking: Boolean = false,
    val bookingResult: BookingData? = null,
    val bookingError: String? = null
)

@HiltViewModel
class BusDetailViewModel @Inject constructor(
    private val busRepository: BusRepository,
    private val tokenManager: TokenManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val busId: String = savedStateHandle["busId"] ?: ""

    private val _uiState = MutableStateFlow(BusDetailUiState())
    val uiState: StateFlow<BusDetailUiState> = _uiState

    init {
        loadBusDetail()
    }

    fun loadBusDetail() {
        if (busId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            busRepository.getBusDetailWithReturnTimes(busId)
                .onSuccess { bus ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        bus = bus,
                        selectedPickup = bus.pickupPoints.firstOrNull()
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load"
                    )
                }
        }
    }

    fun openBookingDialog() {
        viewModelScope.launch {
            val defaultName = tokenManager.userName.firstOrNull().orEmpty()
            val defaultPhone = tokenManager.userMobile.firstOrNull()
                .orEmpty()
                .filter { it.isDigit() }
                .take(10)

            _uiState.value = _uiState.value.copy(
                showBookingDialog = true,
                bookingError = null,
                bookingResult = null,
                seatCount = 1,
                passengerName = defaultName,
                passengerPhone = defaultPhone
            )
        }
    }

    fun updatePassengerName(name: String) {
        _uiState.value = _uiState.value.copy(passengerName = name)
    }

    fun updatePassengerPhone(phone: String) {
        _uiState.value = _uiState.value.copy(passengerPhone = phone.filter { it.isDigit() }.take(10))
    }

    fun dismissBookingDialog() {
        _uiState.value = _uiState.value.copy(showBookingDialog = false)
    }

    fun updateSeatCount(count: Int) {
        val max = _uiState.value.bus?.availableSeats ?: 1
        _uiState.value = _uiState.value.copy(seatCount = count.coerceIn(1, max))
    }

    fun selectPickup(pickup: PickupPointInfo) {
        _uiState.value = _uiState.value.copy(selectedPickup = pickup)
    }

    fun confirmBooking() {
        val state = _uiState.value
        val pickup = state.selectedPickup ?: return
        val bus = state.bus ?: return

        if (state.passengerName.isBlank()) {
            _uiState.value = _uiState.value.copy(bookingError = "Please enter passenger name")
            return
        }
        if (state.passengerPhone.length < 10) {
            _uiState.value = _uiState.value.copy(bookingError = "Please enter a valid 10-digit phone number")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBooking = true, bookingError = null)
            busRepository.createBooking(
                bus.id, pickup.id, state.seatCount,
                state.passengerName.trim(), state.passengerPhone.trim()
            )
                .onSuccess { booking ->
                    val existingName = tokenManager.userName.firstOrNull().orEmpty()
                    if (existingName.isBlank() && state.passengerName.isNotBlank()) {
                        tokenManager.updateUserName(state.passengerName.trim())
                    }
                    _uiState.value = _uiState.value.copy(
                        isBooking = false,
                        bookingResult = booking,
                        showBookingDialog = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isBooking = false,
                        bookingError = e.message ?: "Booking failed"
                    )
                }
        }
    }

    fun dismissBookingResult() {
        _uiState.value = _uiState.value.copy(bookingResult = null)
        loadBusDetail()
    }
}
