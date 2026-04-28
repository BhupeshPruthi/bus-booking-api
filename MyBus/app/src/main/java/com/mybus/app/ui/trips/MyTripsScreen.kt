package com.mybus.app.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.BookingData
import com.mybus.app.ui.util.formatBusScheduleDateTimeFull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTripsScreen(
    onBookingClick: (bookingId: String) -> Unit = {},
    onBack: (() -> Unit)? = null,
    isLoggedIn: Boolean = true,
    onRequireLogin: () -> Unit = {},
    viewModel: MyTripsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Upcoming", "Past", "Failed")

    LaunchedEffect(Unit) {
        viewModel.loadBookings()
    }

    LaunchedEffect(state.requiresLogin) {
        if (state.requiresLogin) onRequireLogin()
    }

    LaunchedEffect(isLoggedIn, state.requiresLogin) {
        if (isLoggedIn && state.requiresLogin) {
            viewModel.loadBookings()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Trips", fontWeight = FontWeight.Bold) },
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
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    val count = when (index) {
                        0 -> state.upcoming.size
                        1 -> state.past.size
                        2 -> state.failed.size
                        else -> 0
                    }
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            if (count > 0) {
                                Text("$title ($count)")
                            } else {
                                Text(title)
                            }
                        }
                    )
                }
            }

            when {
                state.isLoading -> {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                state.error != null -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadBookings() }) { Text("Retry") }
                    }
                }
                state.requiresLogin -> {
                    LoginRequiredView(onLoginClick = onRequireLogin)
                }
                else -> {
                    val bookings = when (selectedTab) {
                        0 -> state.upcoming
                        1 -> state.past
                        2 -> state.failed
                        else -> emptyList()
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (bookings.isEmpty()) {
                            EmptyTripsView(tab = tabs[selectedTab])
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(bookings, key = { it.id }) { booking ->
                                    BookingCard(
                                        booking = booking,
                                        onClick = { onBookingClick(booking.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginRequiredView(onLoginClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Log in to view your trips",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLoginClick) {
            Text("Log in")
        }
    }
}

@Composable
private fun BookingCard(booking: BookingData, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${booking.route?.source ?: ""} \u2192 ${booking.route?.destination ?: ""}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(status = booking.status)
            }

            Spacer(Modifier.height(8.dp))

            booking.bus?.departureTime?.let { departureTime ->
                val tripEndTime = if (booking.bus.tripType == "round_trip") {
                    booking.bus.returnArrivalTime ?: booking.bus.arrivalTime
                } else {
                    booking.bus.arrivalTime
                }
                val scheduleText = if (tripEndTime != null) {
                    "${formatDateTime(departureTime)} \u2192 ${formatDateTime(tripEndTime)}"
                } else {
                    formatDateTime(departureTime)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = scheduleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.EventSeat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    val seatText = if (booking.assignedSeats != null) {
                        "Seats: ${booking.assignedSeats}"
                    } else {
                        "${booking.seatCount} seat(s)"
                    }
                    Text(
                        text = seatText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "\u20B9${booking.totalAmount.toLong()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            booking.pickupPoint?.let { pp ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = pp.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyTripsView(tab: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val (icon, message) = when (tab) {
            "Upcoming" -> Icons.Filled.Luggage to "No upcoming trips.\nBook a bus from the Home tab!"
            "Past" -> Icons.Filled.History to "No past trips yet."
            "Failed" -> Icons.Filled.ErrorOutline to "No failed bookings. Great!"
            else -> Icons.Filled.Luggage to "No trips."
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatDateTime(isoString: String): String {
    return formatBusScheduleDateTimeFull(isoString)
}
