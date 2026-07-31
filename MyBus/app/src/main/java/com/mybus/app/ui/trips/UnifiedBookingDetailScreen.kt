package com.mybus.app.ui.trips

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.UnifiedBookingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedBookingDetailScreen(
    onBack: () -> Unit,
    viewModel: UnifiedBookingDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showCancellation by remember { mutableStateOf(false) }
    var cancellationReason by remember { mutableStateOf("") }
    val cancelsImmediately = state.booking?.let {
        it.bookingType == "stay" && it.rawStatus == "pending"
    } == true

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = { onBack() },
            title = { Text("Unable to continue") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::load) { Text("Retry") } },
            dismissButton = { TextButton(onClick = onBack) { Text("Back") } }
        )
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text("Request received") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text("OK") } }
        )
    }
    if (showCancellation) {
        AlertDialog(
            onDismissRequest = { showCancellation = false },
            title = { Text(if (cancelsImmediately) "Cancel booking" else "Request cancellation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (cancelsImmediately) {
                            "This pending booking will be cancelled immediately."
                        } else {
                            "This request will be sent to the relevant admin for review."
                        }
                    )
                    OutlinedTextField(
                        value = cancellationReason,
                        onValueChange = { cancellationReason = it.take(1000) },
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.requestCancellation(cancellationReason.ifBlank { null })
                    showCancellation = false
                }) { Text(if (cancelsImmediately) "Cancel booking" else "Submit request") }
            },
            dismissButton = {
                TextButton(onClick = { showCancellation = false }) { Text("Back") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Booking details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading && state.booking == null -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            state.booking != null -> UnifiedBookingDetailsContent(
                booking = state.booking!!,
                onCancel = { showCancellation = true },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun UnifiedBookingDetailsContent(
    booking: UnifiedBookingItem,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val details = booking.details
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(booking.bookingType.bookingTypeIcon(), null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(booking.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(booking.bookingType.bookingTypeLabel())
                        }
                    }
                    UnifiedStatusChip(booking.rawStatus)
                    Text(booking.subtitle)
                    DetailRow("Reference", booking.reference)
                    DetailRow("Starts", formatUnifiedDateTime(booking.startsAt))
                    DetailRow("Ends", formatUnifiedDateTime(booking.endsAt))
                    if (booking.bookingType != "pooja" && booking.totalAmount != null) {
                        DetailRow(
                            if (booking.bookingType == "stay" && details.discountAmount > 0) {
                                "Remaining amount"
                            } else {
                                "Total"
                            },
                            formatUnifiedRupees(booking.totalAmount)
                        )
                    }
                }
            }
        }

        when (booking.bookingType) {
            "bus" -> {
                item { DetailSectionTitle("Bus details") }
                details.busName?.let { item { DetailRowCard("Bus", it) } }
                if (details.source != null && details.destination != null) {
                    item { DetailRowCard("Route", "${details.source} → ${details.destination}") }
                }
                details.pickupPoint?.let { item { DetailRowCard("Pickup point", it) } }
                details.assignedSeats?.let { item { DetailRowCard("Seats", it) } }
                    ?: details.seatCount?.let { item { DetailRowCard("Seats", "$it seat(s)") } }
                details.passengerName?.let { item { DetailRowCard("Passenger", it) } }
                details.passengerPhone?.let { item { DetailRowCard("Phone", it) } }
            }
            "stay" -> {
                item {
                    val stayRows = buildList {
                        if (details.checkInDate != null && details.checkOutDate != null) {
                            add("Stay" to "${details.checkInDate} → ${details.checkOutDate}")
                        }
                        details.guestCount?.let { add("Guests" to it.toString()) }
                        details.items.forEach { stayItem ->
                            add(
                                stayItem.unitTypeName to
                                    "${stayItem.quantity} × ${stayItem.nightCount} night(s) · " +
                                    formatUnifiedRupees(stayItem.lineTotal)
                            )
                        }
                        if (details.discountAmount > 0 && details.subtotalAmount != null) {
                            add("Subtotal" to formatUnifiedRupees(details.subtotalAmount))
                            add(
                                "Coupon ${details.couponCode.orEmpty()}" to
                                    "−${formatUnifiedRupees(details.discountAmount)}"
                            )
                            booking.totalAmount?.let {
                                add("Remaining amount" to formatUnifiedRupees(it))
                            }
                        }
                        details.contactName?.let { add("Contact" to it) }
                        details.contactEmail?.let { add("Email" to it) }
                        details.contactPhone?.let { add("Phone" to it) }
                        details.rejectionReason?.let { add("Reason" to it) }
                    }
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            DetailSectionTitle("Stay details")
                            if (stayRows.isNotEmpty()) {
                                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            }
                            stayRows.forEachIndexed { index, (label, value) ->
                                DetailRow(label, value)
                                if (index < stayRows.lastIndex) {
                                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                                }
                            }
                        }
                    }
                }
            }
            "pooja" -> {
                item { DetailSectionTitle("Pooja details") }
                details.place?.let { item { DetailRowCard("Place", it) } }
                details.tokenNumber?.let { item { DetailRowCard("Token", "#$it") } }
                details.memberCount?.let { item { DetailRowCard("Members", it.toString()) } }
                details.city?.let { item { DetailRowCard("City", it) } }
                details.name?.let { item { DetailRowCard("Name", it) } }
                details.phone?.let { item { DetailRowCard("Phone", it) } }
            }
        }

        if ("request_cancellation" in booking.availableActions) {
            item {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Request cancellation")
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DetailSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun DetailRowCard(label: String, value: String) {
    Card {
        DetailRow(label, value, Modifier.padding(14.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
