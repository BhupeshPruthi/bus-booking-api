package com.mybus.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.dto.UnifiedBookingCounts
import com.mybus.app.data.remote.dto.UnifiedBookingItem
import com.mybus.app.data.repository.AuthenticationRequiredException
import com.mybus.app.data.repository.UnifiedBookingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyBookingsUiState(
    val loadingBuckets: Set<String> = emptySet(),
    val error: String? = null,
    val requiresLogin: Boolean = false,
    val typeFilter: String? = null,
    val items: Map<String, List<UnifiedBookingItem>> = emptyMap(),
    val nextCursors: Map<String, String?> = emptyMap(),
    val loadedBuckets: Set<String> = emptySet(),
    val counts: UnifiedBookingCounts = UnifiedBookingCounts()
)

@HiltViewModel
class MyBookingsViewModel @Inject constructor(
    private val repository: UnifiedBookingRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyBookingsUiState())
    val uiState: StateFlow<MyBookingsUiState> = _uiState

    fun configureType(type: String?) {
        val normalized = type?.takeIf { it in setOf("bus", "stay", "pooja") }
        if (_uiState.value.typeFilter == normalized) return
        _uiState.value = MyBookingsUiState(typeFilter = normalized)
    }

    fun load(bucket: String, reset: Boolean = true, limit: Int = 20) {
        if (bucket !in setOf("upcoming", "past", "failed")) return
        val current = _uiState.value
        if (bucket in current.loadingBuckets) return
        if (!reset && current.nextCursors[bucket] == null) return

        _uiState.value = current.copy(
            loadingBuckets = current.loadingBuckets + bucket,
            error = null,
            requiresLogin = false
        )
        viewModelScope.launch {
            if (tokenManager.accessToken.firstOrNull().isNullOrBlank()) {
                val latest = _uiState.value
                _uiState.value = latest.copy(
                    loadingBuckets = latest.loadingBuckets - bucket,
                    requiresLogin = true
                )
                return@launch
            }
            repository.getBookings(
                bucket = bucket,
                types = current.typeFilter,
                cursor = if (reset) null else current.nextCursors[bucket],
                limit = limit
            ).onSuccess { page ->
                val latest = _uiState.value
                val existing = if (reset) emptyList() else latest.items[bucket].orEmpty()
                _uiState.value = latest.copy(
                    loadingBuckets = latest.loadingBuckets - bucket,
                    items = latest.items + (bucket to (existing + page.items).distinctBy { it.id }),
                    nextCursors = latest.nextCursors + (bucket to page.nextCursor),
                    loadedBuckets = latest.loadedBuckets + bucket,
                    counts = page.counts
                )
            }.onFailure { error ->
                if (error is AuthenticationRequiredException) {
                    tokenManager.clear()
                    _uiState.value = MyBookingsUiState(
                        requiresLogin = true,
                        typeFilter = current.typeFilter
                    )
                } else {
                    val latest = _uiState.value
                    _uiState.value = latest.copy(
                        loadingBuckets = latest.loadingBuckets - bucket,
                        error = error.message ?: "Failed to load your bookings"
                    )
                }
            }
        }
    }

    fun loadIfNeeded(bucket: String) {
        if (bucket !in _uiState.value.loadedBuckets) load(bucket)
    }
}
