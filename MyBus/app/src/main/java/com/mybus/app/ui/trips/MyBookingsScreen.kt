package com.mybus.app.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.UnifiedBookingItem
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val bookingBuckets = listOf("upcoming", "past", "failed")
private val bookingBucketLabels = listOf("Upcoming", "Past", "Failed")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    initialType: String? = null,
    onBookingClick: (bookingType: String, bookingId: String) -> Unit,
    onBack: () -> Unit,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    viewModel: MyBookingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val selectedBucket = bookingBuckets[selectedTab]

    LaunchedEffect(initialType) {
        viewModel.configureType(initialType)
        viewModel.load(selectedBucket)
    }
    LaunchedEffect(selectedBucket) {
        viewModel.loadIfNeeded(selectedBucket)
    }
    LaunchedEffect(state.requiresLogin) {
        if (state.requiresLogin) onRequireLogin()
    }
    LaunchedEffect(isLoggedIn, state.requiresLogin) {
        if (isLoggedIn && state.requiresLogin) viewModel.load(selectedBucket)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Bookings", fontWeight = FontWeight.Bold)
                        state.typeFilter?.let {
                            Text(
                                it.bookingTypeLabel(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                bookingBucketLabels.forEachIndexed { index, label ->
                    val count = when (index) {
                        0 -> state.counts.upcoming
                        1 -> state.counts.past
                        else -> state.counts.failed
                    }
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(if (count > 0) "$label ($count)" else label) }
                    )
                }
            }

            val bookings = state.items[selectedBucket].orEmpty()
            val isLoading = selectedBucket in state.loadingBuckets
            when {
                state.requiresLogin -> UnifiedLoginRequired(onRequireLogin)
                isLoading && bookings.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                state.error != null && bookings.isEmpty() -> UnifiedBookingError(
                    message = state.error!!,
                    onRetry = { viewModel.load(selectedBucket) }
                )
                bookings.isEmpty() -> EmptyUnifiedBookings(bucket = selectedBucket)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(bookings, key = { it.id }) { booking ->
                        UnifiedBookingCard(
                            booking = booking,
                            onClick = {
                                onBookingClick(booking.bookingType, booking.bookingId)
                            }
                        )
                    }
                    if (state.nextCursors[selectedBucket] != null) {
                        item {
                            OutlinedButton(
                                onClick = { viewModel.load(selectedBucket, reset = false) },
                                enabled = !isLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Load more")
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
internal fun UnifiedBookingCard(
    booking: UnifiedBookingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        booking.bookingType.bookingTypeIcon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            booking.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            booking.bookingType.bookingTypeLabel(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                UnifiedStatusChip(booking.rawStatus)
            }

            Text(
                booking.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${formatUnifiedDateTime(booking.startsAt)} → ${formatUnifiedDateTime(booking.endsAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    booking.reference,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (booking.bookingType != "pooja" && booking.totalAmount != null) {
                    Text(
                        formatUnifiedRupees(booking.totalAmount),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun UnifiedStatusChip(status: String) {
    val normalized = status.lowercase()
    val color = when (normalized) {
        "confirmed", "completed" -> MaterialTheme.colorScheme.primary
        "rejected", "cancelled" -> MaterialTheme.colorScheme.error
        "expired" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.tertiary
    }
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Text(
            normalized.replace('_', ' ').replaceFirstChar(Char::titlecase),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun UnifiedLoginRequired(onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.AccountCircle, null, Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("Log in to view all your bookings.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onLogin) { Text("Log in") }
    }
}

@Composable
private fun UnifiedBookingError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EmptyUnifiedBookings(bucket: String) {
    val (icon, message) = when (bucket) {
        "past" -> Icons.Filled.History to "No past bookings yet."
        "failed" -> Icons.Filled.ErrorOutline to "No cancelled, rejected, or expired bookings."
        else -> Icons.Filled.Luggage to "No upcoming Bus, Stay, or Pooja bookings."
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center)
    }
}

internal fun String.bookingTypeLabel(): String = when (this) {
    "bus" -> "Bus"
    "stay" -> "Stay"
    "pooja" -> "Pooja"
    else -> replaceFirstChar(Char::titlecase)
}

internal fun String.bookingTypeIcon(): ImageVector = when (this) {
    "bus" -> Icons.Filled.DirectionsBus
    "stay" -> Icons.Filled.Hotel
    "pooja" -> Icons.Filled.SelfImprovement
    else -> Icons.Filled.Luggage
}

internal fun formatUnifiedDateTime(value: String): String = runCatching {
    Instant.parse(value)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
}.getOrDefault(value)

internal fun formatUnifiedRupees(amount: Double): String =
    "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount)
