package com.mybus.app.ui.pooja

import android.util.Log
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

data class PoojaDetailUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAdmin: Boolean = false,
    val pooja: PoojaDetailData? = null,

    val showBookingDialog: Boolean = false,
    val bookingName: String = "",
    val bookingPhone: String = "",
    val bookingLoading: Boolean = false,
    val bookingSuccess: PoojaBookingData? = null
)

@HiltViewModel
class PoojaDetailViewModel @Inject constructor(
    private val poojaRepository: PoojaRepository,
    private val tokenManager: TokenManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val poojaId: String = savedStateHandle["poojaId"] ?: ""

    private val _uiState = MutableStateFlow(PoojaDetailUiState())
    val uiState: StateFlow<PoojaDetailUiState> = _uiState

    init {
        load()
    }

    fun load(forceAdmin: Boolean? = null) {
        if (poojaId.isBlank()) return
        viewModelScope.launch {
            val isAdmin = forceAdmin ?: tokenManager.readEffectiveIsAdmin()
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, isAdmin = isAdmin)

            val result = if (isAdmin) {
                poojaRepository.getAdminPoojaDetail(poojaId)
            } else {
                poojaRepository.getPoojaDetail(poojaId)
            }

            result.onSuccess { pooja ->
                _uiState.value = _uiState.value.copy(isLoading = false, pooja = pooja)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load pooja"
                )
            }
        }
    }

    fun openBookingDialog() {
        viewModelScope.launch {
            val defaultName = tokenManager.userName.firstOrNull()
                .orEmpty()
                .filter { it.isLetter() || it.isWhitespace() }
            val defaultPhone = tokenManager.userMobile.firstOrNull()
                .orEmpty()
                .filter { it.isDigit() }

            _uiState.value = _uiState.value.copy(
                showBookingDialog = true,
                bookingName = defaultName,
                bookingPhone = defaultPhone,
                bookingSuccess = null
            )
        }
    }

    fun closeBookingDialog() {
        _uiState.value = _uiState.value.copy(showBookingDialog = false)
    }

    fun updateBookingName(value: String) {
        _uiState.value = _uiState.value.copy(bookingName = value)
    }

    fun updateBookingPhone(value: String) {
        _uiState.value = _uiState.value.copy(bookingPhone = value)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissBookingSuccess() {
        _uiState.value = _uiState.value.copy(bookingSuccess = null)
    }

    fun bookToken() {
        val state = _uiState.value
        val name = state.bookingName.trim()
        val phone = state.bookingPhone.trim()

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

        viewModelScope.launch {
            Log.d("PoojaUI", "Booking token poojaId=$poojaId name=$name phone=$phone")
            _uiState.value = _uiState.value.copy(bookingLoading = true, error = null)
            poojaRepository.bookToken(poojaId, name, phone)
                .onSuccess { booking ->
                    Log.d("PoojaUI", "Booking success id=${booking.id} status=${booking.status}")
                    val existingName = tokenManager.userName.firstOrNull().orEmpty()
                    if (existingName.isBlank() && name.isNotBlank()) {
                        tokenManager.updateUserName(name)
                    }
                    _uiState.value = _uiState.value.copy(
                        bookingLoading = false,
                        showBookingDialog = false,
                        bookingSuccess = booking
                    )
                    load(_uiState.value.isAdmin)
                }
                .onFailure { e ->
                    Log.d("PoojaUI", "Booking failed error=${e.message}")
                    _uiState.value = _uiState.value.copy(
                        bookingLoading = false,
                        error = e.message ?: "Failed to book token"
                    )
                }
        }
    }
}

