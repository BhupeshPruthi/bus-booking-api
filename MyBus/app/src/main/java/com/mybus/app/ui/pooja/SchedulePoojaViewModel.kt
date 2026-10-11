package com.mybus.app.ui.pooja

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.CreatePoojaRequest
import com.mybus.app.data.remote.dto.PoojaDetailData
import com.mybus.app.data.remote.dto.UpdatePoojaRequest
import com.mybus.app.data.repository.PoojaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SchedulePoojaUiState(
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val place: String = "Mahandipur Balaji",
    val totalTokens: String = "50",
    val isEditMode: Boolean = false,
    val minimumTokenCount: Int = 1,
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedPooja: PoojaDetailData? = null
)

@HiltViewModel
class SchedulePoojaViewModel @Inject constructor(
    private val poojaRepository: PoojaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val poojaId: String = savedStateHandle["poojaId"] ?: ""

    private val _uiState = MutableStateFlow(
        SchedulePoojaUiState(isEditMode = poojaId.isNotBlank())
    )
    val uiState: StateFlow<SchedulePoojaUiState> = _uiState.asStateFlow()

    init {
        if (poojaId.isNotBlank()) loadPooja()
    }

    fun updateDate(value: LocalDate) { _uiState.value = _uiState.value.copy(date = value) }
    fun updateTime(value: LocalTime) { _uiState.value = _uiState.value.copy(time = value) }
    fun updatePlace(value: String) { _uiState.value = _uiState.value.copy(place = value) }
    fun updateTotalTokens(value: String) { _uiState.value = _uiState.value.copy(totalTokens = value) }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun dismissSuccess() { _uiState.value = _uiState.value.copy(savedPooja = null) }

    fun savePooja() {
        val state = _uiState.value
        val date = state.date
        val time = state.time

        if (date == null || time == null) {
            _uiState.value = state.copy(error = "Please select date and time")
            return
        }

        val zoned = ZonedDateTime.of(date, time, ZoneId.systemDefault())
        val scheduledAt = zoned.toInstant().atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val tokens = state.totalTokens.toIntOrNull()
        if (tokens == null || tokens < state.minimumTokenCount) {
            val message = if (state.minimumTokenCount > 1) {
                "Tokens cannot be less than confirmed token #${state.minimumTokenCount}"
            } else {
                "Please enter a valid token count"
            }
            _uiState.value = state.copy(error = message)
            return
        }

        val place = state.place.trim()
        if (place.isBlank()) {
            _uiState.value = state.copy(error = "Please enter a place")
            return
        }

        viewModelScope.launch {
            Log.d("PoojaUI", "Save pooja id=$poojaId scheduledAt=$scheduledAt place=$place totalTokens=$tokens")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = if (state.isEditMode) {
                poojaRepository.updatePooja(
                    poojaId,
                    UpdatePoojaRequest(
                        scheduledAt = scheduledAt,
                        place = place,
                        totalTokens = tokens
                    )
                )
            } else {
                poojaRepository.createPooja(
                    CreatePoojaRequest(
                        scheduledAt = scheduledAt,
                        place = place,
                        totalTokens = tokens
                    )
                )
            }
            result.onSuccess { saved ->
                Log.d("PoojaUI", "Save pooja success id=${saved.id}")
                _uiState.value = _uiState.value.copy(isLoading = false, savedPooja = saved)
            }.onFailure { e ->
                Log.d("PoojaUI", "Save pooja failed error=${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: if (state.isEditMode) {
                        "Failed to update pooja"
                    } else {
                        "Failed to create pooja"
                    }
                )
            }
        }
    }

    private fun loadPooja() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            poojaRepository.getAdminPoojaDetail(poojaId)
                .onSuccess { pooja ->
                    val scheduled = runCatching {
                        java.time.Instant.parse(pooja.scheduledAt).atZone(ZoneId.systemDefault())
                    }.getOrNull()
                    val highestConfirmedToken = pooja.bookings.orEmpty()
                        .filter { it.status == "confirmed" }
                        .mapNotNull { it.tokenNumber }
                        .maxOrNull() ?: 1
                    _uiState.value = _uiState.value.copy(
                        date = scheduled?.toLocalDate(),
                        time = scheduled?.toLocalTime(),
                        place = pooja.place,
                        totalTokens = pooja.totalTokens.toString(),
                        minimumTokenCount = highestConfirmedToken,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load pooja"
                    )
                }
        }
    }
}
