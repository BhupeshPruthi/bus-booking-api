package com.mybus.app.ui.pooja

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.CreatePoojaRequest
import com.mybus.app.data.remote.dto.PoojaDetailData
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
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdPooja: PoojaDetailData? = null
)

@HiltViewModel
class SchedulePoojaViewModel @Inject constructor(
    private val poojaRepository: PoojaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchedulePoojaUiState())
    val uiState: StateFlow<SchedulePoojaUiState> = _uiState.asStateFlow()

    fun updateDate(value: LocalDate) { _uiState.value = _uiState.value.copy(date = value) }
    fun updateTime(value: LocalTime) { _uiState.value = _uiState.value.copy(time = value) }
    fun updatePlace(value: String) { _uiState.value = _uiState.value.copy(place = value) }
    fun updateTotalTokens(value: String) { _uiState.value = _uiState.value.copy(totalTokens = value) }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun dismissSuccess() { _uiState.value = SchedulePoojaUiState() }

    fun createPooja() {
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
        if (tokens == null || tokens < 1) {
            _uiState.value = state.copy(error = "Please enter a valid token count")
            return
        }

        val place = state.place.trim()
        if (place.isBlank()) {
            _uiState.value = state.copy(error = "Please enter a place")
            return
        }

        viewModelScope.launch {
            Log.d("PoojaUI", "Create pooja scheduledAt=$scheduledAt place=$place totalTokens=$tokens")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            poojaRepository.createPooja(
                CreatePoojaRequest(
                    scheduledAt = scheduledAt,
                    place = place,
                    totalTokens = tokens
                )
            ).onSuccess { created ->
                Log.d("PoojaUI", "Create pooja success id=${created.id}")
                _uiState.value = _uiState.value.copy(isLoading = false, createdPooja = created)
            }.onFailure { e ->
                Log.d("PoojaUI", "Create pooja failed error=${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to create pooja"
                )
            }
        }
    }
}

