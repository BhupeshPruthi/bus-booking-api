package com.mybus.app.ui.pooja

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.dto.PoojaDetailData
import com.mybus.app.data.repository.PoojaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PoojaDetailUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAdmin: Boolean = false,
    val pooja: PoojaDetailData? = null,
    val cancelInProgressId: String? = null
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun cancelBookingAsAdmin(bookingId: String) {
        if (poojaId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cancelInProgressId = bookingId, error = null)
            poojaRepository.cancelPoojaBookingAsAdmin(poojaId, bookingId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(cancelInProgressId = null)
                    load(true)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        cancelInProgressId = null,
                        error = e.message ?: "Failed to cancel token"
                    )
                }
        }
    }
}
