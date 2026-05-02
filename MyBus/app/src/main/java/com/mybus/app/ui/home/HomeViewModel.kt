package com.mybus.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.dto.BusListItem
import com.mybus.app.data.repository.BusRepository
import com.mybus.app.ui.util.seatAvailabilityWithPair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val buses: List<BusListItem> = emptyList(),
    val isAdmin: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val busRepository: BusRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            tokenManager.effectiveIsAdmin.distinctUntilChanged().collect {
                loadBuses()
            }
        }
    }

    fun loadBuses() {
        viewModelScope.launch {
            val isAdmin = tokenManager.readEffectiveIsAdmin()
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, isAdmin = isAdmin)

            val result = if (isAdmin) {
                busRepository.getAdminBuses()
            } else {
                busRepository.getConsumerBuses()
            }

            result.onSuccess { buses ->
                val filtered = if (isAdmin) {
                    val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
                    buses.filter { bus ->
                        bus.arrivalTime?.let {
                            try {
                                Instant.parse(it).isAfter(cutoff)
                            } catch (_: Exception) {
                                true
                            }
                        } ?: true
                    }
                } else {
                    buses
                }

                val withPairedAvailability = filtered.withPairedRoundTripAvailability()
                val busMap = withPairedAvailability.associateBy { it.id }
                val removedIds = mutableSetOf<String>()
                for (bus in withPairedAvailability) {
                    if (bus.tripType == "round_trip" && bus.returnBusId != null
                        && bus.id !in removedIds
                    ) {
                        val returnBus = busMap[bus.returnBusId]
                        if (returnBus != null) {
                            val depA = try { Instant.parse(bus.departureTime) } catch (_: Exception) { Instant.MAX }
                            val depB = try { Instant.parse(returnBus.departureTime) } catch (_: Exception) { Instant.MAX }
                            if (depA <= depB) {
                                removedIds.add(returnBus.id)
                            } else {
                                removedIds.add(bus.id)
                            }
                        }
                    }
                }
                val deduplicated = withPairedAvailability.filter { it.id !in removedIds }

                _uiState.value = _uiState.value.copy(isLoading = false, buses = deduplicated)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Something went wrong"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun List<BusListItem>.withPairedRoundTripAvailability(): List<BusListItem> {
        val busMap = associateBy { it.id }
        return map { bus ->
            val pair = bus.returnBusId?.let { busMap[it] }
            val availability = bus.seatAvailabilityWithPair(pair)
            bus.copy(
                bookedSeats = availability.reservedSeats,
                availableSeats = availability.availableSeats,
                bookableSeats = availability.bookableSeats
            )
        }
    }
}
