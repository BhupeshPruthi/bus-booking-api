package com.mybus.app.ui.stay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.dto.StayBooking
import com.mybus.app.data.remote.dto.StayCancellation
import com.mybus.app.data.remote.dto.StayCoupon
import com.mybus.app.data.remote.dto.StayDailyOccupancyReport
import com.mybus.app.data.remote.dto.CreateStayBookingRequest
import com.mybus.app.data.repository.StayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StayAdminUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val bookings: List<StayBooking> = emptyList(),
    val bookingStatus: String? = "pending",
    val bookingSearch: String = "",
    val bookingPage: Int = 1,
    val bookingTotal: Int = 0,
    val pendingTotal: Int = 0,
    val confirmedTotal: Int = 0,
    val dailyOccupancy: StayDailyOccupancyReport? = null,
    val cancellations: List<StayCancellation> = emptyList(),
    val cancellationPage: Int = 1,
    val cancellationTotal: Int = 0,
    val coupons: List<StayCoupon> = emptyList()
)

@HiltViewModel
class StayAdminViewModel @Inject constructor(
    private val repository: StayRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(StayAdminUiState())
    val uiState: StateFlow<StayAdminUiState> = _uiState

    init {
        refreshDashboard()
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val pending = async { repository.getAdminBookings(status = "pending", limit = 1) }
            val confirmed = async { repository.getAdminBookings(status = "confirmed", limit = 1) }
            val cancellations = async {
                repository.getCancellationRequests(status = "pending", limit = 1)
            }
            val results = listOf(
                pending.await(),
                confirmed.await(),
                cancellations.await()
            )
            val error = results.firstNotNullOfOrNull { it.exceptionOrNull() }
            if (error != null) {
                showError(error, "Failed to load Stay administration")
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                pendingTotal = (results[0].getOrThrow() as com.mybus.app.data.remote.dto.StayPage<*>).total,
                confirmedTotal = (results[1].getOrThrow() as com.mybus.app.data.remote.dto.StayPage<*>).total,
                cancellationTotal = (results[2].getOrThrow() as com.mybus.app.data.remote.dto.StayPage<*>).total
            )
        }
    }

    fun loadBookings(status: String?, search: String = "", reset: Boolean = true) {
        val nextPage = if (reset) 1 else _uiState.value.bookingPage + 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getAdminBookings(status, search.ifBlank { null }, nextPage, 50)
                .onSuccess { page ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        bookings = if (reset) page.items else _uiState.value.bookings + page.items,
                        bookingStatus = status,
                        bookingSearch = search,
                        bookingPage = page.page,
                        bookingTotal = page.total
                    )
                }
                .onFailure { showError(it, "Failed to load Stay bookings") }
        }
    }

    fun loadCancellations(reset: Boolean = true) {
        val nextPage = if (reset) 1 else _uiState.value.cancellationPage + 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getCancellationRequests("pending", nextPage, 50)
                .onSuccess { page ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        cancellations = if (reset) page.items else {
                            _uiState.value.cancellations + page.items
                        },
                        cancellationPage = page.page,
                        cancellationTotal = page.total
                    )
                }
                .onFailure { showError(it, "Failed to load cancellation requests") }
        }
    }

    fun loadDailyOccupancy() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getDailyOccupancy()
                .onSuccess { report ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        dailyOccupancy = report
                    )
                }
                .onFailure { showError(it, "Failed to load daily occupancy") }
        }
    }

    fun loadCoupons() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getCoupons()
                .onSuccess { coupons ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        coupons = coupons
                    )
                }
                .onFailure { showError(it, "Failed to load coupons") }
        }
    }

    private fun act(success: String, operation: suspend () -> Result<*>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)
            operation()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(message = success)
                    refreshDashboard()
                    loadBookings(
                        _uiState.value.bookingStatus,
                        _uiState.value.bookingSearch
                    )
                }
                .onFailure { showError(it, success) }
        }
    }

    fun confirm(id: String) = act("Booking confirmed") { repository.confirmBooking(id) }

    fun createAdminBooking(request: CreateStayBookingRequest) {
        viewModelScope.launch {
            val email = tokenManager.userEmail.first().orEmpty().trim()
            if (email.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    error = "Unable to find your login email. Please sign in again."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)
            repository.createAdminBooking(request.copy(contactEmail = email))
                .onSuccess {
                    _uiState.value = _uiState.value.copy(message = "Stay booking confirmed")
                    refreshDashboard()
                    loadBookings("confirmed")
                }
                .onFailure { showError(it, "Failed to create Stay booking") }
        }
    }

    fun cancelAdminBooking(
        id: String,
        refundDecision: String,
        refundAmount: Double?,
        reason: String?
    ) = act("Stay booking cancelled") {
        repository.cancelAdminBooking(id, refundDecision, refundAmount, reason)
    }

    fun reject(id: String, reason: String) = act("Booking rejected") {
        repository.rejectBooking(id, reason)
    }

    fun decideCancellation(
        id: String,
        action: String,
        refund: String?,
        amount: Double?,
        reason: String?
    ) = act("Cancellation processed") {
        repository.decideCancellation(id, action, refund, amount, reason)
    }

    fun createCoupon(
        code: String,
        discountAmount: Double,
        startDate: String,
        endDate: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)
            repository.createCoupon(code, discountAmount, startDate, endDate)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(message = "Coupon created")
                    loadCoupons()
                }
                .onFailure { showError(it, "Failed to create coupon") }
        }
    }

    fun deactivateCoupon(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)
            repository.deactivateCoupon(id)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(message = "Coupon deactivated")
                    loadCoupons()
                }
                .onFailure { showError(it, "Failed to deactivate coupon") }
        }
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(error = null, message = null)
    }

    private fun showError(error: Throwable, fallback: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = error.message ?: fallback
        )
    }
}
