package com.mybus.app.ui.stay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.dto.CreateStayBookingRequest
import com.mybus.app.data.remote.dto.StayBooking
import com.mybus.app.data.remote.dto.StayBookingItemRequest
import com.mybus.app.data.remote.dto.StayCatalog
import com.mybus.app.data.remote.dto.StayQuote
import com.mybus.app.data.remote.dto.StayQuoteRequest
import com.mybus.app.data.repository.StayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class StayUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val catalog: StayCatalog? = null,
    val checkInDate: LocalDate = LocalDate.now().plusDays(1),
    val checkOutDate: LocalDate = LocalDate.now().plusDays(2),
    val quantities: Map<String, Int> = emptyMap(),
    val quote: StayQuote? = null,
    val contactName: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val guestCount: String = "1",
    val customerNote: String = "",
    val policyAccepted: Boolean = false,
    val submittedBooking: StayBooking? = null,
    val myBookings: List<StayBooking> = emptyList(),
    val myBookingsPage: Int = 1,
    val myBookingsTotal: Int = 0
)

@HiltViewModel
class StayViewModel @Inject constructor(
    private val repository: StayRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(StayUiState())
    val uiState: StateFlow<StayUiState> = _uiState

    init {
        loadCatalog()
        prefillContact()
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getCatalog()
                .onSuccess { catalog ->
                    _uiState.value = _uiState.value.copy(isLoading = false, catalog = catalog)
                }
                .onFailure { showError(it, "Failed to load Stay information") }
        }
    }

    private fun prefillContact() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                contactName = tokenManager.userName.first().orEmpty(),
                contactEmail = tokenManager.userEmail.first().orEmpty(),
                contactPhone = tokenManager.userMobile.first().orEmpty()
            )
        }
    }

    fun setDates(checkIn: LocalDate, checkOut: LocalDate) {
        val normalizedCheckout = when {
            !checkOut.isAfter(checkIn) -> checkIn.plusDays(1)
            checkOut.isAfter(checkIn.plusDays(7)) -> checkIn.plusDays(7)
            else -> checkOut
        }
        _uiState.value = _uiState.value.copy(
            checkInDate = checkIn,
            checkOutDate = normalizedCheckout,
            quote = null,
            error = null
        )
    }

    fun changeQuantity(code: String, delta: Int) {
        val current = _uiState.value.quantities[code] ?: 0
        val max = _uiState.value.catalog?.unitTypes?.firstOrNull { it.code == code }?.totalUnits ?: 20
        val next = (current + delta).coerceIn(0, max)
        _uiState.value = _uiState.value.copy(
            quantities = _uiState.value.quantities.toMutableMap().apply { put(code, next) },
            quote = null,
            error = null
        )
    }

    private fun itemRequests(): List<StayBookingItemRequest> =
        _uiState.value.quantities
            .filterValues { it > 0 }
            .map { StayBookingItemRequest(it.key, it.value) }

    fun checkAvailability(onAvailable: () -> Unit = {}) {
        val state = _uiState.value
        val items = itemRequests()
        if (items.isEmpty()) {
            _uiState.value = state.copy(error = "Select at least one room or hall")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            repository.quote(
                StayQuoteRequest(
                    checkInDate = state.checkInDate.toString(),
                    checkOutDate = state.checkOutDate.toString(),
                    items = items
                )
            ).onSuccess { quote ->
                _uiState.value = _uiState.value.copy(isLoading = false, quote = quote)
                if (quote.canFulfill) onAvailable()
            }.onFailure { showError(it, "Failed to check availability") }
        }
    }

    fun updateContact(
        name: String? = null,
        phone: String? = null,
        guests: String? = null,
        note: String? = null,
        accepted: Boolean? = null
    ) {
        val state = _uiState.value
        _uiState.value = state.copy(
            contactName = name ?: state.contactName,
            contactPhone = phone ?: state.contactPhone,
            guestCount = guests ?: state.guestCount,
            customerNote = note ?: state.customerNote,
            policyAccepted = accepted ?: state.policyAccepted,
            error = null
        )
    }

    fun submitBooking() {
        val state = _uiState.value
        val quote = state.quote
        val guests = state.guestCount.toIntOrNull()
        when {
            quote == null || !quote.canFulfill -> {
                _uiState.value = state.copy(error = "Check availability before submitting")
                return
            }
            state.contactName.isBlank() -> {
                _uiState.value = state.copy(error = "Name is required")
                return
            }
            state.contactPhone.isBlank() -> {
                _uiState.value = state.copy(error = "Phone number is required")
                return
            }
            guests == null || guests < 1 -> {
                _uiState.value = state.copy(error = "Guest count must be at least 1")
                return
            }
            !state.policyAccepted -> {
                _uiState.value = state.copy(error = "Please accept the cancellation policy")
                return
            }
        }
        viewModelScope.launch {
            val loggedInEmail = tokenManager.userEmail.first().orEmpty().trim()
            if (loggedInEmail.isBlank()) {
                _uiState.value = state.copy(error = "Unable to find your login email. Please sign in again.")
                return@launch
            }
            _uiState.value = state.copy(isLoading = true, error = null)
            repository.createBooking(
                CreateStayBookingRequest(
                    checkInDate = state.checkInDate.toString(),
                    checkOutDate = state.checkOutDate.toString(),
                    items = itemRequests(),
                    guestCount = guests,
                    contactName = state.contactName.trim(),
                    contactEmail = loggedInEmail,
                    contactPhone = state.contactPhone.trim(),
                    customerNote = state.customerNote.trim().ifBlank { null }
                )
            ).onSuccess { booking ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    submittedBooking = booking
                )
            }.onFailure { showError(it, "Failed to submit Stay request") }
        }
    }

    fun loadMyBookings(reset: Boolean = true) {
        val nextPage = if (reset) 1 else _uiState.value.myBookingsPage + 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getMyBookings(nextPage)
                .onSuccess { page ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        myBookings = if (reset) page.items else {
                            _uiState.value.myBookings + page.items
                        },
                        myBookingsPage = page.page,
                        myBookingsTotal = page.total
                    )
                }
                .onFailure { showError(it, "Failed to load your stays") }
        }
    }

    fun loadMoreMyBookings() = loadMyBookings(reset = false)

    fun requestCancellation(bookingId: String, reason: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.requestCancellation(bookingId, reason)
                .onSuccess { loadMyBookings() }
                .onFailure { showError(it, "Failed to request cancellation") }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSubmission() {
        _uiState.value = _uiState.value.copy(submittedBooking = null)
    }

    private fun showError(error: Throwable, fallback: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = error.message ?: fallback
        )
    }
}
