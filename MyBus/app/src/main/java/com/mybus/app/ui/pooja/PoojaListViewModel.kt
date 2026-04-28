package com.mybus.app.ui.pooja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.dto.PoojaBookingData
import com.mybus.app.data.remote.dto.PoojaListItem
import com.mybus.app.data.repository.PoojaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PoojaListUiState(
    val isLoading: Boolean = false,
    val poojas: List<PoojaListItem> = emptyList(),
    val isAdmin: Boolean = false,
    val error: String? = null,

    val showBookingDialog: Boolean = false,
    val bookingPooja: PoojaListItem? = null,
    val bookingName: String = "",
    val bookingPhone: String = "",
    val bookingLoading: Boolean = false,
    val bookingSuccess: PoojaBookingData? = null,
    val bookingError: String? = null
)

@HiltViewModel
class PoojaListViewModel @Inject constructor(
    private val poojaRepository: PoojaRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PoojaListUiState())
    val uiState: StateFlow<PoojaListUiState> = _uiState

    init {
        viewModelScope.launch {
            tokenManager.effectiveIsAdmin.distinctUntilChanged().collect {
                loadPoojas()
            }
        }
    }

    fun loadPoojas() {
        viewModelScope.launch {
            val isAdmin = tokenManager.readEffectiveIsAdmin()
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, isAdmin = isAdmin)

            val result = if (isAdmin) {
                poojaRepository.getAdminPoojas()
            } else {
                poojaRepository.getUpcomingPoojas()
            }

            result.onSuccess { list ->
                _uiState.value = _uiState.value.copy(isLoading = false, poojas = list)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load poojas"
                )
            }
        }
    }

    fun openBookingDialog(pooja: PoojaListItem) {
        if (pooja.availableTokens <= 0) {
            _uiState.value = _uiState.value.copy(
                bookingError = "No tokens available for this pooja"
            )
            return
        }

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
                showBookingDialog = true,
                bookingPooja = pooja,
                bookingName = defaultName,
                bookingPhone = defaultPhone,
                bookingLoading = false,
                bookingError = null,
                bookingSuccess = null
            )
        }
    }

    fun closeBookingDialog() {
        _uiState.value = _uiState.value.copy(showBookingDialog = false)
    }

    fun updateBookingName(value: String) {
        val filtered = value
            .filter { it.isLetter() || it.isWhitespace() }
            .take(50)
        _uiState.value = _uiState.value.copy(bookingName = filtered, bookingError = null)
    }

    fun updateBookingPhone(value: String) {
        val filtered = value
            .filter { it.isDigit() }
            .take(15)
        _uiState.value = _uiState.value.copy(bookingPhone = filtered, bookingError = null)
    }

    fun clearBookingError() {
        _uiState.value = _uiState.value.copy(bookingError = null)
    }

    fun dismissBookingSuccess() {
        _uiState.value = _uiState.value.copy(bookingSuccess = null)
    }

    fun bookSelectedPoojaToken() {
        val state = _uiState.value
        val pooja = state.bookingPooja ?: run {
            _uiState.value = state.copy(bookingError = "Please select a pooja")
            return
        }

        val name = state.bookingName.trim()
        val phone = state.bookingPhone.trim()

        if (name.isBlank()) {
            _uiState.value = state.copy(bookingError = "Please enter your name")
            return
        }
        if (!name.matches(Regex("^[\\p{L} ]+$"))) {
            _uiState.value = state.copy(bookingError = "Name should contain only letters")
            return
        }
        if (!phone.matches(Regex("^[0-9]{10,15}$"))) {
            _uiState.value = state.copy(bookingError = "Please enter a valid phone number")
            return
        }
        if (pooja.availableTokens <= 0) {
            _uiState.value = state.copy(bookingError = "No tokens available for this pooja")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(bookingLoading = true, bookingError = null)
            poojaRepository.bookToken(pooja.id, name, phone)
                .onSuccess { booking ->
                    val existingName = tokenManager.userName.firstOrNull().orEmpty()
                    if (existingName.isBlank() && name.isNotBlank()) {
                        tokenManager.updateUserName(name)
                    }

                    _uiState.value = _uiState.value.copy(
                        bookingLoading = false,
                        showBookingDialog = false,
                        bookingSuccess = booking
                    )
                    loadPoojas()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        bookingLoading = false,
                        bookingError = e.message ?: "Failed to book token"
                    )
                }
        }
    }
}

