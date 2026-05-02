package com.mybus.app.ui.addbus

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.ui.theme.AppOutlinedButton
import com.mybus.app.ui.theme.outlinedControlLabelStyle
import com.mybus.app.ui.theme.outlinedControlShape
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddBusScreen(
    viewModel: AddBusViewModel = hiltViewModel(),
    onNavigateToStops: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    /** After success dialog "Done": e.g. pop back to Active Buses tab. */
    onSuccessDone: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    if (uiState.createdTrip != null) {
        val trip = uiState.createdTrip!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccess() },
            title = { Text("Trip Created") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${trip.outbound.route.source} to ${trip.outbound.route.destination}")
                    Text("Bus: ${trip.outbound.busName}")
                    Text("Seats: ${trip.outbound.totalSeats} | Fare: ₹${trip.outbound.price}")
                    Text("Status: ${trip.outbound.status}")
                    if (trip.returnTrip != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Return: ${trip.returnTrip.busName}")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissSuccess()
                        onSuccessDone()
                    }
                ) { Text("Done") }
            }
        )
    }

    if (uiState.error != null) {
        val err = uiState.error!!
        val isFormValidation = err.startsWith("Fix the following")
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(if (isFormValidation) "Create trip blocked" else "Error") },
            text = {
                Text(
                    err,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Add Bus Trip") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Source & Destination
            OutlinedTextField(
                value = uiState.source,
                onValueChange = { viewModel.updateSource(it) },
                label = { Text("Source") },
                placeholder = { Text("e.g. Delhi") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.destination,
                onValueChange = { viewModel.updateDestination(it) },
                label = { Text("Destination") },
                placeholder = { Text("e.g. Mumbai") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Departure Date & Time
            SectionLabel("Departure")
            DateTimePicker(
                date = uiState.departureDate,
                time = uiState.departureTime,
                onDateSelected = { viewModel.updateDepartureDate(it) },
                onTimeSelected = { viewModel.updateDepartureTime(it) }
            )
            SectionLabel("Arrival Back To Origin")
            DateTimePicker(
                date = uiState.arrivalDate,
                time = uiState.arrivalTime,
                onDateSelected = { viewModel.updateArrivalDate(it) },
                onTimeSelected = { viewModel.updateArrivalTime(it) }
            )

            SectionLabel("Seat")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.totalSeats,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) viewModel.updateTotalSeats(it) },
                    label = { Text("Total seats") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = uiState.reservedSeatsFromStart,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) viewModel.updateReservedSeatsFromStart(it) },
                    label = { Text("Reserved") },
                    placeholder = { Text("e.g. 10") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = uiState.price,
                    onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) viewModel.updatePrice(it) },
                    label = { Text("Price (\u20B9)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            val totalInt = uiState.totalSeats.toIntOrNull()
            val reservedInt = uiState.reservedSeatsFromStart.toIntOrNull() ?: 0
            val seatsForSale = if (totalInt != null && reservedInt >= 0 && reservedInt < totalInt) {
                totalInt - reservedInt
            } else {
                null
            }
            if (seatsForSale != null) {
                Text(
                    text = "Up to $seatsForSale seat(s) can be booked (sales from seat #${reservedInt + 1} to #$totalInt).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Contact Info
            SectionLabel("Point of Contact")
            OutlinedTextField(
                value = uiState.contactName,
                onValueChange = { viewModel.updateContactName(it) },
                label = { Text("Contact Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.contactPhone,
                onValueChange = { if (it.length <= 15 && it.all { c -> c.isDigit() || c == '+' }) viewModel.updateContactPhone(it) },
                label = { Text("Contact Phone") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Stops
            SectionLabel("Stops (In Between)")
            val selectedStops = uiState.stops.filter { it.selected }
            AppOutlinedButton(
                onClick = onNavigateToStops,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selectedStops.size} of ${uiState.stops.size} stops selected",
                        style = outlinedControlLabelStyle(),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
            if (selectedStops.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        8.dp,
                        Alignment.CenterHorizontally
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedStops.forEach { stop ->
                        AssistChip(
                            onClick = {},
                            label = { Text(stop.name, style = MaterialTheme.typography.bodySmall) },
                            shape = outlinedControlShape(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Submit
            // Always tappable (unless loading); validation runs in ViewModel and shows a dialog listing issues.
            Button(
                onClick = { viewModel.createTrip() },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Trip")
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun DateTimePicker(
    date: LocalDate?,
    time: LocalTime?,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (LocalTime) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    val label = when {
        date != null && time != null ->
            "${date.format(dateFormatter)} · ${time.format(timeFormatter)}"
        else -> "Select date & time"
    }

    AppOutlinedButton(
        onClick = {
            val initialDate = date ?: LocalDate.now()
            val initialTime = time ?: LocalTime.of(9, 0)
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val pickedDate = LocalDate.of(year, month + 1, day)
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            onDateSelected(pickedDate)
                            onTimeSelected(LocalTime.of(hour, minute))
                        },
                        initialTime.hour,
                        initialTime.minute,
                        false
                    ).show()
                },
                initialDate.year,
                initialDate.monthValue - 1,
                initialDate.dayOfMonth
            ).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = outlinedControlLabelStyle())
    }
}
