package com.mybus.app.ui.pooja

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.dto.PoojaBookingData
import com.mybus.app.data.remote.dto.PoojaDetailData
import com.mybus.app.data.repository.PoojaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PoojaBookingUiState(
    val isLoading: Boolean = false,
    val pooja: PoojaDetailData? = null,
    val error: String? = null,
    val name: String = "",
    val phone: String = "",
    val memberCount: String = "1",
    val city: String = DEFAULT_POOJA_CITY,
    val isBooking: Boolean = false,
    val bookingSuccess: PoojaBookingData? = null
)

private const val DEFAULT_POOJA_CITY = "Delhi - NCR"
private const val MAX_POOJA_MEMBERS = 10

@HiltViewModel
class PoojaBookingViewModel @Inject constructor(
    private val poojaRepository: PoojaRepository,
    private val tokenManager: TokenManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val poojaId: String = savedStateHandle["poojaId"] ?: ""

    private val _uiState = MutableStateFlow(PoojaBookingUiState())
    val uiState: StateFlow<PoojaBookingUiState> = _uiState

    init {
        load()
    }

    fun load() {
        if (poojaId.isBlank()) return

        viewModelScope.launch {
            val defaultName = tokenManager.userName.firstOrNull()
                .orEmpty()
                .filter { it.isLetter() || it.isWhitespace() }
                .take(50)
            val defaultPhone = tokenManager.userMobile.firstOrNull()
                .orEmpty()
                .filter { it.isDigit() }
                .take(15)

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                name = _uiState.value.name.ifBlank { defaultName },
                phone = _uiState.value.phone.ifBlank { defaultPhone }
            )

            poojaRepository.getPoojaDetail(poojaId)
                .onSuccess { pooja ->
                    _uiState.value = _uiState.value.copy(isLoading = false, pooja = pooja)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load pooja"
                    )
                }
        }
    }

    fun updateName(value: String) {
        val filtered = value
            .filter { it.isLetter() || it.isWhitespace() }
            .take(50)
        _uiState.value = _uiState.value.copy(name = filtered, error = null)
    }

    fun updatePhone(value: String) {
        val filtered = value
            .filter { it.isDigit() }
            .take(15)
        _uiState.value = _uiState.value.copy(phone = filtered, error = null)
    }

    fun updateMemberCount(value: String) {
        val filtered = value
            .filter { it.isDigit() }
            .take(2)
        _uiState.value = _uiState.value.copy(memberCount = filtered, error = null)
    }

    fun updateCity(value: String) {
        _uiState.value = _uiState.value.copy(city = value.take(100), error = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun bookToken() {
        val state = _uiState.value
        val pooja = state.pooja
        val name = state.name.trim()
        val phone = state.phone.trim()
        val memberCount = state.memberCount.toIntOrNull()
        val city = state.city.trim().ifBlank { DEFAULT_POOJA_CITY }

        if (name.isBlank()) {
            _uiState.value = state.copy(error = "Please enter your name")
            return
        }
        if (!name.matches(Regex("^[\\p{L} ]+$"))) {
            _uiState.value = state.copy(error = "Name should contain only letters")
            return
        }
        if (!phone.matches(Regex("^[0-9]{10,15}$"))) {
            _uiState.value = state.copy(error = "Please enter a valid phone number")
            return
        }
        if (memberCount == null || memberCount !in 1..MAX_POOJA_MEMBERS) {
            _uiState.value = state.copy(error = "Members must be between 1 and $MAX_POOJA_MEMBERS")
            return
        }
        if (city.isBlank()) {
            _uiState.value = state.copy(error = "Please enter your city")
            return
        }
        if (pooja == null) {
            _uiState.value = state.copy(error = "Pooja details are still loading")
            return
        }
        val bookingStatus = poojaBookingStatus(
            scheduledAt = pooja.scheduledAt,
            availableTokens = pooja.availableTokens,
            serverStatus = pooja.bookingStatus
        )
        val canBook = canBookPooja(
            scheduledAt = pooja.scheduledAt,
            availableTokens = pooja.availableTokens,
            serverCanBook = pooja.canBook,
            serverStatus = pooja.bookingStatus
        )
        if (!canBook) {
            val message = when (bookingStatus) {
                POOJA_BOOKING_NOT_STARTED -> "Pooja booking has not started yet"
                POOJA_BOOKING_FULL -> "No tokens available for this pooja"
                else -> "This pooja has expired"
            }
            _uiState.value = state.copy(error = message)
            return
        }
        if (pooja.availableTokens <= 0) {
            _uiState.value = state.copy(error = "No tokens available for this pooja")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBooking = true, error = null)
            poojaRepository.bookToken(poojaId, name, phone, memberCount, city)
                .onSuccess { booking ->
                    val existingName = tokenManager.userName.firstOrNull().orEmpty()
                    if (existingName.isBlank() && name.isNotBlank()) {
                        tokenManager.updateUserName(name)
                    }
                    _uiState.value = _uiState.value.copy(
                        isBooking = false,
                        bookingSuccess = booking
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isBooking = false,
                        error = e.message ?: "Failed to book token"
                    )
                }
        }
    }
}
