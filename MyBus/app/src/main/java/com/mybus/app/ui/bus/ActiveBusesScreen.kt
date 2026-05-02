package com.mybus.app.ui.bus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.BusListItem
import com.mybus.app.ui.home.HomeViewModel
import com.mybus.app.ui.theme.AppOutlinedPillReadOnly
import com.mybus.app.ui.util.formatBusScheduleDateTime
import com.mybus.app.ui.util.formatRouteTitle
import com.mybus.app.ui.util.seatAvailability

private val ActiveBusCardBackground = Color(0xFFF5F6FA)
private val ActiveBusCardTextBlack = Color.Black
private val ActiveBusCardPillBorder = Color.Black.copy(alpha = 0.35f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveBusesScreen(
    onBack: () -> Unit,
    onBusClick: (busId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadBuses()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadBuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Active Buses", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading && state.buses.isEmpty() && state.error == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null && state.buses.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadBuses() }) { Text("Retry") }
                }
            } else if (!state.isLoading && state.buses.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.DirectionsBus, contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No active buses found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.buses, key = { it.id }) { bus ->
                        if (state.isAdmin) {
                            AdminBusCard(bus = bus, onClick = { onBusClick(bus.id) })
                        } else {
                            ConsumerBusCard(bus = bus, onClick = { onBusClick(bus.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminBusCard(bus: BusListItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = ActiveBusCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = formatRouteTitle(bus.route.source, bus.route.destination, bus.tripType),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ActiveBusCardTextBlack,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatTripDates(bus),
                style = MaterialTheme.typography.bodyMedium,
                color = ActiveBusCardTextBlack
            )
            Spacer(Modifier.height(6.dp))
            SeatAvailabilityLine(bus = bus)
        }
    }
}

@Composable
private fun ConsumerBusCard(bus: BusListItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = ActiveBusCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DirectionsBus, contentDescription = null,
                    modifier = Modifier.size(24.dp), tint = ActiveBusCardTextBlack
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatRouteTitle(bus.route.source, bus.route.destination, bus.tripType),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ActiveBusCardTextBlack,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                formatTripDates(bus),
                style = MaterialTheme.typography.bodySmall,
                color = ActiveBusCardTextBlack,
            )
            Spacer(Modifier.height(8.dp))
            SeatAvailabilityLine(bus = bus)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "\u20B9${bus.price.toLong()} / seat",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ActiveBusCardTextBlack
                )
            }
        }
    }
}

@Composable
private fun SeatAvailabilityLine(bus: BusListItem) {
    val availability = bus.seatAvailability()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.EventSeat,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = ActiveBusCardTextBlack
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${availability.availableSeats} available / ${availability.bookableSeats} seats",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = ActiveBusCardTextBlack
        )
    }
}

private fun formatTripDates(bus: BusListItem): String {
    val outbound = formatBusScheduleDateTime(bus.departureTime)
    val finalArrival = if (bus.tripType == "round_trip") {
        bus.returnArrivalTime ?: bus.arrivalTime
    } else {
        bus.arrivalTime
    }
    if (finalArrival != null) {
        return "$outbound  \u2192  ${formatBusScheduleDateTime(finalArrival)}"
    }
    return outbound
}
