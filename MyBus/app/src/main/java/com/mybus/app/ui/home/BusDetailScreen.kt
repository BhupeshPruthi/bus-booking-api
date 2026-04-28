package com.mybus.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.BusDetailData
import com.mybus.app.data.remote.dto.PickupPointInfo
import com.mybus.app.ui.components.BusBookingPromoBanner
import com.mybus.app.ui.components.StopListTimeline
import com.mybus.app.ui.components.StopTimelineDisplay
import com.mybus.app.ui.util.formatBusScheduleDateTimeFull
import com.mybus.app.ui.util.formatRouteTitle

private val DetailRowIconSize = 20.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusDetailScreen(
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onBack: () -> Unit,
    onBookingSuccess: () -> Unit = {},
    viewModel: BusDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bus Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            state.bus?.let { bus ->
                if (bus.availableSeats > 0) {
                    Surface(tonalElevation = 3.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "₹${bus.price.toLong()} / seat",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${bus.availableSeats} seats left",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    if (isLoggedIn) {
                                        viewModel.openBookingDialog()
                                    } else {
                                        onRequireLogin()
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.ConfirmationNumber, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Book Ticket")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            state.error != null -> {
                Column(
                    Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadBusDetail() }) { Text("Retry") }
                }
            }
            state.bus != null -> {
                BusDetailContent(
                    bus = state.bus!!,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    if (state.showBookingDialog) {
        BookingDialog(
            bus = state.bus!!,
            seatCount = state.seatCount,
            passengerName = state.passengerName,
            passengerPhone = state.passengerPhone,
            selectedPickup = state.selectedPickup,
            isBooking = state.isBooking,
            bookingError = state.bookingError,
            onSeatCountChange = { viewModel.updateSeatCount(it) },
            onNameChange = { viewModel.updatePassengerName(it) },
            onPhoneChange = { viewModel.updatePassengerPhone(it) },
            onPickupSelect = { viewModel.selectPickup(it) },
            onConfirm = { viewModel.confirmBooking() },
            onDismiss = { viewModel.dismissBookingDialog() }
        )
    }

    state.bookingResult?.let { booking ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissBookingResult() },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
            title = { Text("Booking Requested!") },
            text = {
                Column {
                    Text("${booking.seatCount} seat(s) requested successfully.")
                    booking.assignedSeats?.let { seats ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Seat Numbers: $seats",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Total: \u20B9${booking.totalAmount.toLong()}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Status: ${booking.status.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your request will be reviewed by the admin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissBookingResult()
                    onBookingSuccess()
                }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun BusDetailContent(bus: BusDetailData, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatRouteTitle(bus.route.source, bus.route.destination, bus.tripType),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Journey Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))

                    val journeyIconTint = MaterialTheme.colorScheme.onSurfaceVariant
                    val finalArrival = if (bus.tripType == "round_trip") {
                        bus.returnArrivalTime ?: bus.arrivalTime
                    } else {
                        bus.arrivalTime
                    }

                    DetailRow(
                        icon = Icons.Filled.FlightTakeoff,
                        label = "Departure",
                        value = formatDateTimeFull(bus.departureTime),
                        iconTint = journeyIconTint
                    )
                    finalArrival?.let {
                        DetailRow(
                            icon = Icons.Filled.FlightLand,
                            label = "Arrival",
                            value = formatDateTimeFull(it),
                            iconTint = journeyIconTint
                        )
                    }
                    DetailRow(
                        icon = Icons.Filled.SyncAlt,
                        label = "Trip Type",
                        value = if (bus.tripType == "round_trip") "Round Way" else "One Way",
                        iconTint = journeyIconTint
                    )
                    DetailRow(
                        icon = Icons.Filled.EventSeat,
                        label = "Seats",
                        value = "${bus.availableSeats} available / ${bus.totalSeats} total",
                        iconTint = journeyIconTint
                    )
                }
            }
        }

        if (bus.pickupPoints.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Stops", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        StopListTimeline(
                            stops = bus.pickupPoints.map { p ->
                                StopTimelineDisplay(
                                    title = p.name,
                                    subtitle = p.address?.takeIf { it.isNotBlank() && it != p.name },
                                )
                            },
                        )
                    }
                }
            }
        }

        if (bus.contactName != null || bus.contactPhone != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Contact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        bus.contactName?.let {
                            DetailRow(icon = Icons.Filled.Person, label = "Name", value = it)
                        }
                        bus.contactPhone?.let {
                            DetailRow(icon = Icons.Filled.Phone, label = "Phone", value = it)
                        }
                    }
                }
            }
        }

        // Room above bottom bar + system gesture / nav bar
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    iconTint: Color? = null,
) {
    val resolvedTint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = null,
            modifier = Modifier.size(DetailRowIconSize),
            tint = resolvedTint
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingDialog(
    bus: BusDetailData,
    seatCount: Int,
    passengerName: String,
    passengerPhone: String,
    selectedPickup: PickupPointInfo?,
    isBooking: Boolean,
    bookingError: String?,
    onSeatCountChange: (Int) -> Unit,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPickupSelect: (PickupPointInfo) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isBooking) onDismiss() },
        title = { Text("Book Ticket") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    formatRouteTitle(bus.route.source, bus.route.destination, bus.tripType),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = passengerName,
                    onValueChange = onNameChange,
                    label = { Text("Passenger Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = passengerPhone,
                    onValueChange = onPhoneChange,
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Seats:", modifier = Modifier.width(60.dp))
                    IconButton(
                        onClick = { onSeatCountChange(seatCount - 1) },
                        enabled = seatCount > 1
                    ) { Icon(Icons.Filled.Remove, "Decrease") }
                    Text(
                        "$seatCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = { onSeatCountChange(seatCount + 1) },
                        enabled = seatCount < bus.availableSeats
                    ) { Icon(Icons.Filled.Add, "Increase") }
                }

                if (bus.pickupPoints.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedPickup?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pickup Point") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            bus.pickupPoints.forEach { pickup ->
                                DropdownMenuItem(
                                    text = { Text(pickup.name) },
                                    onClick = {
                                        onPickupSelect(pickup)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    "Total: \u20B9${(bus.price * seatCount).toLong()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                bookingError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isBooking && selectedPickup != null
            ) {
                if (isBooking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBooking) { Text("Cancel") }
        }
    )
}

private fun formatDateTimeFull(isoString: String): String {
    return formatBusScheduleDateTimeFull(isoString)
}
