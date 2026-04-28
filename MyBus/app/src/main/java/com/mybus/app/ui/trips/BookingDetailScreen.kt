package com.mybus.app.ui.trips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.BookingData
import com.mybus.app.ui.util.formatBusScheduleDateTimeFull
import java.time.Instant
import java.time.temporal.ChronoUnit

private val InfoRowIconSize = 20.dp
private const val CancellationCutoffHours = 48L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailScreen(
    onBack: () -> Unit,
    viewModel: BookingDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Booking Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
                    Button(onClick = { viewModel.loadBookingDetail() }) { Text("Retry") }
                }
            }
            state.booking != null -> {
                BookingDetailContent(
                    booking = state.booking!!,
                    actionMessage = state.actionMessage,
                    isActionError = state.isActionError,
                    isCancellationInProgress = state.isCancellationInProgress,
                    onRequestCancellation = viewModel::requestCancellation,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun BookingDetailContent(
    booking: BookingData,
    actionMessage: String?,
    isActionError: Boolean,
    isCancellationInProgress: Boolean,
    onRequestCancellation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCancellationDialog by remember { mutableStateOf(false) }
    val canCancelFromUser = canCancelFromUser(booking)
    val cancellationButtonText = when (booking.status) {
        "confirmed" -> "Request Cancellation"
        else -> "Cancel Request"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Request Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    StatusBadge(status = booking.status)
                }
                actionMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActionError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Trip Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                val tripIconTint = MaterialTheme.colorScheme.onSurfaceVariant

                booking.route?.let { route ->
                    InfoRow(
                        icon = Icons.Filled.Route,
                        label = "Route",
                        value = "${route.source} \u2192 ${route.destination}",
                        iconTint = tripIconTint
                    )
                }

                booking.bus?.let { bus ->
                    InfoRow(
                        icon = Icons.Filled.DirectionsBus,
                        label = "Bus",
                        value = bus.name,
                        iconTint = tripIconTint
                    )
                    InfoRow(
                        icon = Icons.Filled.FlightTakeoff,
                        label = "Departure",
                        value = formatDateTimeFull(bus.departureTime),
                        iconTint = tripIconTint
                    )
                    val tripEndTime = if (bus.tripType == "round_trip") {
                        bus.returnArrivalTime ?: bus.arrivalTime
                    } else {
                        bus.arrivalTime
                    }
                    tripEndTime?.let {
                        InfoRow(
                            icon = Icons.Filled.FlightLand,
                            label = "Arrival",
                            value = formatDateTimeFull(it),
                            iconTint = tripIconTint
                        )
                    }
                }

                booking.pickupPoint?.let { pp ->
                    InfoRow(
                        icon = Icons.Filled.LocationOn,
                        label = "Pickup",
                        value = pp.name,
                        iconTint = tripIconTint
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Passenger Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                booking.passengerName?.let {
                    InfoRow(icon = Icons.Filled.Person, label = "Name", value = it)
                }
                booking.passengerPhone?.let {
                    InfoRow(icon = Icons.Filled.Phone, label = "Phone", value = it)
                }
                InfoRow(
                    icon = Icons.Filled.EventSeat,
                    label = "Seats",
                    value = "${booking.seatCount}"
                )
                booking.assignedSeats?.let {
                    InfoRow(
                        icon = Icons.Filled.ConfirmationNumber,
                        label = "Seat No.",
                        value = it
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Payment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                InfoRow(
                    icon = Icons.Filled.CurrencyRupee,
                    label = "Total",
                    value = "\u20B9${booking.totalAmount.toLong()}"
                )
            }
        }

        booking.createdAt?.let {
            Text(
                text = "Booked on ${formatDateTimeFull(it)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        if (booking.status in userCancellableStatuses) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (canCancelFromUser) {
                        OutlinedButton(
                            onClick = { showCancellationDialog = true },
                            enabled = !isCancellationInProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            if (isCancellationInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(cancellationButtonText)
                        }
                    } else {
                        Text(
                            "Cancellation request window closed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showCancellationDialog) {
        val isConfirmedBooking = booking.status == "confirmed"
        AlertDialog(
            onDismissRequest = { showCancellationDialog = false },
            icon = { Icon(Icons.Filled.Cancel, contentDescription = null) },
            title = { Text(if (isConfirmedBooking) "Request cancellation" else "Cancel request") },
            text = {
                Text(
                    if (isConfirmedBooking) {
                        "Send this confirmed booking to admin for cancellation approval?"
                    } else {
                        "Cancel this booking request? The assigned seats will be released immediately."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancellationDialog = false
                        onRequestCancellation()
                    }
                ) { Text(if (isConfirmedBooking) "Send Request" else "Cancel Request") }
            },
            dismissButton = {
                TextButton(onClick = { showCancellationDialog = false }) {
                    Text("Keep Booking")
                }
            }
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, color) = when (status) {
        "pending" -> "Pending" to MaterialTheme.colorScheme.tertiary
        "confirmed" -> "Confirmed" to MaterialTheme.colorScheme.primary
        "cancellation_requested" -> "Cancellation Requested" to MaterialTheme.colorScheme.secondary
        "payment_uploaded" -> "Payment Sent" to MaterialTheme.colorScheme.secondary
        "rejected" -> "Rejected" to MaterialTheme.colorScheme.error
        "cancelled" -> "Cancelled" to MaterialTheme.colorScheme.error
        "expired" -> "Expired" to MaterialTheme.colorScheme.outline
        else -> status.replaceFirstChar { it.uppercase() } to MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InfoRow(
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
            modifier = Modifier.size(InfoRowIconSize),
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

private fun formatDateTimeFull(isoString: String): String {
    return formatBusScheduleDateTimeFull(isoString)
}

private val userCancellableStatuses = setOf("pending", "payment_uploaded", "confirmed")

private fun canCancelFromUser(booking: BookingData): Boolean {
    if (booking.status in setOf("pending", "payment_uploaded")) return true
    if (booking.status != "confirmed") return false
    val departure = booking.bus?.departureTime ?: return false
    return try {
        val departureTime = Instant.parse(departure)
        !departureTime.isBefore(Instant.now().plus(CancellationCutoffHours, ChronoUnit.HOURS))
    } catch (_: Exception) {
        false
    }
}
