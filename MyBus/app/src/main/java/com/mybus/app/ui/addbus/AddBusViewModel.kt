package com.mybus.app.ui.addbus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.CreateTripRequest
import com.mybus.app.data.remote.dto.StopRequest
import com.mybus.app.data.remote.dto.TripData
import com.mybus.app.data.repository.TripRepository
import com.mybus.app.ui.util.toBusScheduleApiDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class PredefinedStop(val name: String, val selected: Boolean = true)

val DEFAULT_STOPS = listOf(
    PredefinedStop("Rani Bagh"),
    PredefinedStop("Rajori Garden"),
    PredefinedStop("Dhola Kuan"),
    PredefinedStop("Rajiv Chowk"),
)

data class AddBusUiState(
    val source: String = "Delhi",
    val destination: String = "Mahandipur Balaji",
    val departureDate: LocalDate? = null,
    val departureTime: LocalTime? = null,
    val arrivalDate: LocalDate? = null,
    val arrivalTime: LocalTime? = null,
    val totalSeats: String = "",
    /** Seats numbered 1..[reserved] are not for sale; booking starts at seat [reserved]+1. Bookable count = total − reserved. */
    val reservedSeatsFromStart: String = "0",
    val price: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
    val stops: List<PredefinedStop> = DEFAULT_STOPS,
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdTrip: TripData? = null
)

@HiltViewModel
class AddBusViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddBusUiState())
    val uiState: StateFlow<AddBusUiState> = _uiState.asStateFlow()

    fun updateSource(value: String) { _uiState.value = _uiState.value.copy(source = value) }
    fun updateDestination(value: String) { _uiState.value = _uiState.value.copy(destination = value) }
    fun updateDepartureDate(value: LocalDate) { _uiState.value = _uiState.value.copy(departureDate = value) }
    fun updateDepartureTime(value: LocalTime) { _uiState.value = _uiState.value.copy(departureTime = value) }
    fun updateArrivalDate(value: LocalDate) { _uiState.value = _uiState.value.copy(arrivalDate = value) }
    fun updateArrivalTime(value: LocalTime) { _uiState.value = _uiState.value.copy(arrivalTime = value) }
    fun updateTotalSeats(value: String) { _uiState.value = _uiState.value.copy(totalSeats = value) }
    fun updateReservedSeatsFromStart(value: String) { _uiState.value = _uiState.value.copy(reservedSeatsFromStart = value) }
    fun updatePrice(value: String) { _uiState.value = _uiState.value.copy(price = value) }
    fun updateContactName(value: String) { _uiState.value = _uiState.value.copy(contactName = value) }
    fun updateContactPhone(value: String) { _uiState.value = _uiState.value.copy(contactPhone = value) }

    fun toggleStop(index: Int) {
        val updated = _uiState.value.stops.toMutableList()
        if (index in updated.indices) {
            val stop = updated[index]
            updated[index] = stop.copy(selected = !stop.selected)
            _uiState.value = _uiState.value.copy(stops = updated)
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun dismissSuccess() { _uiState.value = AddBusUiState(stops = DEFAULT_STOPS) }

    /**
     * Returns null if the form is OK, otherwise a message listing every missing/invalid field
     * (button stays enabled; user taps Create Trip to see this).
     */
    private fun validateCreateTripForm(state: AddBusUiState): String? {
        val issues = mutableListOf<String>()
        if (state.source.isBlank()) issues.add("Source is empty")
        if (state.destination.isBlank()) issues.add("Destination is empty")
        if (state.departureDate == null || state.departureTime == null) {
            issues.add("Departure date and time must both be set")
        }
        if (state.arrivalDate == null || state.arrivalTime == null) {
            issues.add("Arrival date and time must both be set")
        }
        val outbound = if (state.departureDate != null && state.departureTime != null) {
            state.departureDate.atTime(state.departureTime)
        } else {
            null
        }
        val outboundArrival = if (state.arrivalDate != null && state.arrivalTime != null) {
            state.arrivalDate.atTime(state.arrivalTime)
        } else {
            null
        }
        if (outbound != null && outboundArrival != null && !outboundArrival.isAfter(outbound)) {
            issues.add("Arrival must be after departure")
        }
        if (state.totalSeats.isBlank()) issues.add("Total seats is empty")
        else {
            val s = state.totalSeats.toIntOrNull()
            if (s == null || s < 1) issues.add("Total seats must be a positive number")
        }
        val totalSeatsInt = state.totalSeats.toIntOrNull()
        val reserved = state.reservedSeatsFromStart.toIntOrNull() ?: 0
        if (state.reservedSeatsFromStart.isNotBlank() && reserved < 0) {
            issues.add("Reserved seats cannot be negative")
        }
        if (totalSeatsInt != null && reserved >= totalSeatsInt) {
            issues.add("Reserved seats must be less than total seats (so there is at least one bookable seat)")
        }
        if (state.price.isBlank()) issues.add("Price is empty")
        else {
            val p = state.price.toDoubleOrNull()
            if (p == null || p <= 0) issues.add("Price must be a number greater than 0")
        }
        if (state.contactName.isBlank()) issues.add("Contact name is empty")
        if (state.contactPhone.isBlank()) issues.add("Contact phone is empty")

        if (issues.isEmpty()) return null
        return buildString {
            appendLine("Fix the following before creating the trip:")
            appendLine()
            issues.forEach { appendLine("• $it") }
        }.trimEnd()
    }

    fun createTrip() {
        val state = _uiState.value

        validateCreateTripForm(state)?.let { msg ->
            _uiState.value = state.copy(error = msg)
            return
        }

        // Bus schedules are India wall-clock times. Convert that schedule time to one UTC instant
        // before sending it to the API so all clients display the same intended trip time.
        val depDateTime = toBusScheduleApiDateTime(state.departureDate!!, state.departureTime!!)
        val arrDateTime = toBusScheduleApiDateTime(state.arrivalDate!!, state.arrivalTime!!)

        val seats = state.totalSeats.toIntOrNull()!!
        val reserved = state.reservedSeatsFromStart.toIntOrNull() ?: 0
        val firstBookableSeatNumber = reserved + 1
        val price = state.price.toDoubleOrNull()!!

        val request = CreateTripRequest(
            source = state.source.trim(),
            destination = state.destination.trim(),
            departureTime = depDateTime,
            arrivalTime = arrDateTime,
            totalSeats = seats,
            seatStartNumber = firstBookableSeatNumber,
            price = price,
            contactName = state.contactName.trim(),
            contactPhone = state.contactPhone.trim(),
            tripType = "round_trip",
            stops = state.stops
                .filter { it.selected }
                .map { StopRequest(it.name.trim(), null) },
            returnDepartureTime = null,
            returnArrivalTime = arrDateTime
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            tripRepository.createTrip(request)
                .onSuccess { tripData ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        createdTrip = tripData
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
        }
    }
}
