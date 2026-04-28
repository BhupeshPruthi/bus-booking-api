package com.mybus.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mybus.app.data.remote.dto.BookingData
import com.mybus.app.data.remote.dto.EventListItem
import com.mybus.app.R
import com.mybus.app.ui.events.EventsViewModel
import com.mybus.app.ui.theme.AppOutlinedButtonLabel
import com.mybus.app.ui.trips.MyTripsViewModel
import com.mybus.app.ui.util.formatBusScheduleDateTime
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardShadowElevation = 6.dp
private val CardShadowColor = Color(0x40000000) // 25% black – visible on both light & dark

private fun Modifier.cardShadow(shape: RoundedCornerShape) = this.shadow(
    elevation = CardShadowElevation,
    shape = shape,
    ambientColor = CardShadowColor,
    spotColor = CardShadowColor,
)

/** Subtle border visible in both light & dark themes. */
@Composable
private fun cardBorder() = BorderStroke(
    width = 0.5.dp,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(
    isAdmin: Boolean,
    isLoggedIn: Boolean,
    onOpenMyTrips: () -> Unit,
    /** Opens booking detail for a specific trip (home upcoming cards). */
    onOpenBookingDetail: (bookingId: String) -> Unit,
    onAddEventClick: () -> Unit,
    onOpenProfile: () -> Unit,
    /** Opens live events page in-app (upcoming event card). */
    onOpenLiveEvents: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UpcomingEventsSection(onOpenLiveEvents = onOpenLiveEvents)

            UpcomingTripsSection(
                isLoggedIn = isLoggedIn,
                onOpenMyTrips = onOpenMyTrips,
                onOpenBookingDetail = onOpenBookingDetail
            )

            GalleryHomeSection()

            if (isAdmin) {
                ActionCard(
                    icon = Icons.Filled.Event,
                    title = "Add Event",
                    subtitle = "Create an upcoming event",
                    onClick = onAddEventClick
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        Surface(
            onClick = onOpenProfile,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp)
                .size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            shadowElevation = 4.dp,
            border = BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun UpcomingEventsSection(
    onOpenLiveEvents: () -> Unit,
    viewModel: EventsViewModel = hiltViewModel(),
) {
    // Refresh events every time the home tab becomes visible
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadEvents()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.events.isEmpty()) {
        return
    }

    val sorted = state.events.sortedBy { e ->
        parseInstantOrNull(e.eventDate)?.toEpochMilli() ?: Long.MAX_VALUE
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Upcoming Events",
            showTrailingIcon = false
        )

        val pagerState = rememberPagerState(pageCount = { sorted.size })

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
            pageSpacing = 14.dp
        ) { page ->
            UpcomingEventCard(
                event = sorted[page],
                onOpenLiveEvents = onOpenLiveEvents,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (sorted.size > 1) {
            UpcomingEventsPageIndicator(
                pageCount = sorted.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun UpcomingEventsPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (selected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
            )
        }
    }
}

@Composable
private fun UpcomingEventCard(
    event: EventListItem,
    onOpenLiveEvents: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateChipText = formatEventDate(event.eventDate)

    val cardShape = RoundedCornerShape(24.dp)
    val bannerTopShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Card(
        modifier = modifier
            .cardShadow(cardShape)
            .clickable(onClick = onOpenLiveEvents),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorder(),
        shape = cardShape,
    ) {
        Column {
            Image(
                painter = painterResource(R.drawable.upcoming_event_banner),
                contentDescription = event.header,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(bannerTopShape),
                contentScale = ContentScale.Crop,
            )
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = event.header,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        color = Color(0xFF6B17BD),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = dateChipText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            maxLines = 1
                        )
                    }
                }

                Text(
                    text = event.subHeader,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatEventDate(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val zoned = instant.atZone(ZoneId.systemDefault())
        zoned.format(DateTimeFormatter.ofPattern("dd MMM"))
    } catch (_: Exception) {
        isoString
    }
}

@Composable
private fun UpcomingTripsSection(
    isLoggedIn: Boolean,
    onOpenMyTrips: () -> Unit,
    onOpenBookingDetail: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Upcoming Trips",
            onClick = onOpenMyTrips,
            showTrailingIcon = false
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .cardShadow(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenMyTrips),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = cardBorder(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Let's embark on our spiritual journey together.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Click here to see your Trips",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun GuestUpcomingTripsBody(
    onOpenMyTrips: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorder(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Login to view your upcoming trips.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppOutlinedButtonLabel(text = "Login", onClick = onOpenMyTrips)
        }
    }
}

@Composable
private fun UpcomingTripsBody(
    isLoading: Boolean,
    error: String?,
    upcoming: List<BookingData>,
    onRetry: () -> Unit,
    onOpenBookingDetail: (String) -> Unit
) {
    if (isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    when {
        error != null && upcoming.isEmpty() -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cardShadow(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = cardBorder(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    AppOutlinedButtonLabel(text = "Retry", onClick = onRetry)
                }
            }
        }

        !isLoading && upcoming.isEmpty() -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cardShadow(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = cardBorder(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "No upcoming trips yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Go to the Bus tab to book a seat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        else -> {
            val sorted = upcoming.sortedBy { b ->
                parseInstantOrNull(b.bus?.departureTime)?.toEpochMilli() ?: Long.MAX_VALUE
            }
            val pagerState = rememberPagerState(pageCount = { sorted.size })

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 2.dp),
                pageSpacing = 14.dp
            ) { page ->
                val booking = sorted[page]
                UpcomingTripCard(
                    booking = booking,
                    onBookingClick = { onOpenBookingDetail(booking.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun UpcomingTripCard(
    booking: BookingData,
    onBookingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val routeText = buildString {
        append(booking.route?.source.orEmpty())
        append(" \u2192 ")
        append(booking.route?.destination.orEmpty())
    }.trim()

    val depText = booking.bus?.let { bus ->
        val outbound = formatBusScheduleDateTime(bus.departureTime)
        val finalArrival = if (bus.tripType == "round_trip") {
            bus.returnArrivalTime ?: bus.arrivalTime
        } else {
            bus.arrivalTime
        }
        if (finalArrival != null) {
            "$outbound  \u2192  ${formatBusScheduleDateTime(finalArrival)}"
        } else {
            outbound
        }
    } ?: "—"
    val pickupText = booking.pickupPoint?.name?.takeIf { it.isNotBlank() }
    val seatsText = booking.assignedSeats?.takeIf { it.isNotBlank() }?.let { "Seats: $it" }
        ?: "${booking.seatCount} seat(s)"

    Card(
        modifier = modifier
            .cardShadow(RoundedCornerShape(24.dp))
            .clickable(onClick = onBookingClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorder(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = routeText.ifBlank { "Upcoming Trip" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                TripStatusChip(status = booking.status)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = depText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (pickupText != null) {
                Text(
                    text = pickupText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.EventSeat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = seatsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = formatRupees(booking.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TripStatusChip(status: String) {
    val normalized = status.trim().lowercase()
    val label = normalized
        .split('_', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { char -> char.titlecase() } }

    val (bg, fg) = when (normalized) {
        "confirmed", "approved" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "pending", "cancellation_requested" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onClick: (() -> Unit)? = null,
    showTrailingIcon: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (showTrailingIcon) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "View all",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatRupees(amount: Double): String {
    return try {
        val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
        "₹" + nf.format(amount.toLong())
    } catch (_: Exception) {
        "₹" + amount.toLong()
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val actionCardShape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(actionCardShape)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorder(),
        shape = actionCardShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun parseInstantOrNull(isoString: String?): Instant? {
    if (isoString.isNullOrBlank()) return null
    return try {
        Instant.parse(isoString)
    } catch (_: Exception) {
        null
    }
}
