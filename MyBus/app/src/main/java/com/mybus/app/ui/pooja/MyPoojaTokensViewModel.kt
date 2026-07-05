package com.mybus.app.ui.pooja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.dto.PoojaTokenHistoryItem
import com.mybus.app.data.repository.AuthenticationRequiredException
import com.mybus.app.data.repository.PoojaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyPoojaTokensUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val requiresLogin: Boolean = false,
    val tokens: List<PoojaTokenHistoryItem> = emptyList()
)

@HiltViewModel
class MyPoojaTokensViewModel @Inject constructor(
    private val poojaRepository: PoojaRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPoojaTokensUiState())
    val uiState: StateFlow<MyPoojaTokensUiState> = _uiState

    fun loadTokens() {
        viewModelScope.launch {
            if (tokenManager.accessToken.firstOrNull().isNullOrBlank()) {
                _uiState.value = MyPoojaTokensUiState(requiresLogin = true)
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                requiresLogin = false
            )

            poojaRepository.getMyPoojaBookings()
                .onSuccess { tokens ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        requiresLogin = false,
                        tokens = tokens
                    )
                }
                .onFailure { error ->
                    if (error is AuthenticationRequiredException) {
                        tokenManager.clear()
                        _uiState.value = MyPoojaTokensUiState(requiresLogin = true)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load pooja tokens"
                        )
                    }
                }
        }
    }
}
