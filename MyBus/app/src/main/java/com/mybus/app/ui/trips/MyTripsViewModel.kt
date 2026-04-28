package com.mybus.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.dto.BookingData
import com.mybus.app.data.repository.AuthenticationRequiredException
import com.mybus.app.data.repository.BusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class MyTripsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val requiresLogin: Boolean = false,
    val upcoming: List<BookingData> = emptyList(),
    val past: List<BookingData> = emptyList(),
    val failed: List<BookingData> = emptyList()
)

@HiltViewModel
class MyTripsViewModel @Inject constructor(
    private val busRepository: BusRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyTripsUiState())
    val uiState: StateFlow<MyTripsUiState> = _uiState

    fun loadBookings() {
        viewModelScope.launch {
            if (tokenManager.accessToken.firstOrNull().isNullOrBlank()) {
                _uiState.value = MyTripsUiState(requiresLogin = true)
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true, error = null, requiresLogin = false)
            busRepository.getMyBookings()
                .onSuccess { bookings ->
                    val now = Instant.now()
                    val failedStatuses = setOf("rejected", "cancelled", "expired")
                    /** Still awaiting admin / payment review — keep in Upcoming (e.g. Home) even after departure time. */
                    val awaitingResolutionStatuses = setOf("pending", "payment_uploaded", "cancellation_requested")
                    val upcoming = mutableListOf<BookingData>()
                    val past = mutableListOf<BookingData>()
                    val failed = mutableListOf<BookingData>()

                    for (booking in bookings) {
                        when {
                            booking.status in failedStatuses -> failed.add(booking)
                            booking.status in awaitingResolutionStatuses -> upcoming.add(booking)
                            isDeparted(booking, now) -> past.add(booking)
                            else -> upcoming.add(booking)
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        requiresLogin = false,
                        upcoming = upcoming,
                        past = past,
                        failed = failed
                    )
                }
                .onFailure { e ->
                    if (e is AuthenticationRequiredException) {
                        tokenManager.clear()
                        _uiState.value = MyTripsUiState(requiresLogin = true)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load bookings"
                        )
                    }
                }
        }
    }

    private fun isDeparted(booking: BookingData, now: Instant): Boolean {
        val depTime = booking.bus?.departureTime ?: return false
        return try {
            Instant.parse(depTime).isBefore(now)
        } catch (_: Exception) {
            false
        }
    }
}
