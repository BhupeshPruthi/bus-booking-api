package com.mybus.app.ui.pooja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.dto.PoojaListItem
import com.mybus.app.data.repository.PoojaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PoojaListUiState(
    val isLoading: Boolean = false,
    val poojas: List<PoojaListItem> = emptyList(),
    val isAdmin: Boolean = false,
    val error: String? = null
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
}
