package com.mybus.app.ui.stay

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.StayBooking
import com.mybus.app.data.remote.dto.StayUnitType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class CustomerStayPage { SEARCH, BOOKING_DETAILS, MY_STAYS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StayCustomerScreen(
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onOpenMyBookings: () -> Unit,
    viewModel: StayViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var page by remember { mutableStateOf(CustomerStayPage.SEARCH) }
    var cancellationBooking by remember { mutableStateOf<StayBooking?>(null) }
    var cancellationReason by remember { mutableStateOf("") }

    BackHandler(enabled = page == CustomerStayPage.BOOKING_DETAILS) {
        page = CustomerStayPage.SEARCH
    }

    LaunchedEffect(page, isLoggedIn) {
        if (page == CustomerStayPage.MY_STAYS && isLoggedIn) viewModel.loadMyBookings()
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Unable to continue") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }
    state.submittedBooking?.let { booking ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Request submitted") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reference: ${booking.reference}", fontWeight = FontWeight.Bold)
                    Text("Please contact the Admin to confirm your booking and complete the payment.")
                    Text("Total: ₹${booking.totalAmount.asRupees()}")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearSubmission()
                    onOpenMyBookings()
                }) { Text("View My Stays") }
            }
        )
    }
    cancellationBooking?.let { booking ->
        AlertDialog(
            onDismissRequest = { cancellationBooking = null },
            title = { Text("Request cancellation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Requests made at least 48 hours before 12:00 noon check-in are eligible for a 100% refund.")
                    OutlinedTextField(
                        value = cancellationReason,
                        onValueChange = { cancellationReason = it.take(1000) },
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = { TextButton(onClick = { cancellationBooking = null }) { Text("Back") } },
            confirmButton = {
                Button(onClick = {
                    viewModel.requestCancellation(booking.id, cancellationReason.ifBlank { null })
                    cancellationBooking = null
                    cancellationReason = ""
                }) { Text("Submit request") }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (page) {
                            CustomerStayPage.SEARCH -> "Rooms & halls"
                            CustomerStayPage.BOOKING_DETAILS -> "Booking details"
                            CustomerStayPage.MY_STAYS -> "My Stays"
                        }
                    )
                },
                navigationIcon = {
                    if (page == CustomerStayPage.BOOKING_DETAILS) {
                        IconButton(onClick = { page = CustomerStayPage.SEARCH }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Stay")
                        }
                    }
                },
                actions = {
                    if (page != CustomerStayPage.BOOKING_DETAILS) {
                        IconButton(onClick = {
                            if (!isLoggedIn) {
                                onRequireLogin()
                            } else if (page == CustomerStayPage.SEARCH) {
                                onOpenMyBookings()
                            } else {
                                page = CustomerStayPage.SEARCH
                            }
                        }) {
                            Icon(
                                if (page == CustomerStayPage.SEARCH) Icons.Filled.History else Icons.Filled.Hotel,
                                contentDescription = if (page == CustomerStayPage.SEARCH) "My Stays" else "Book a Stay"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (page) {
            CustomerStayPage.SEARCH -> StaySearchContent(
                state = state,
                onAvailable = { page = CustomerStayPage.BOOKING_DETAILS },
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
            CustomerStayPage.BOOKING_DETAILS -> StayBookingDetailsContent(
                state = state,
                isLoggedIn = isLoggedIn,
                onRequireLogin = onRequireLogin,
                onBack = { page = CustomerStayPage.SEARCH },
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
            CustomerStayPage.MY_STAYS -> MyStaysContent(
                state = state,
                onBookStay = { page = CustomerStayPage.SEARCH },
                onCancel = {
                    cancellationBooking = it
                    cancellationReason = ""
                },
                onLoadMore = viewModel::loadMoreMyBookings,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun StaySearchContent(
    state: StayUiState,
    onAvailable: () -> Unit,
    viewModel: StayViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    StayDateButton(
                        label = "Check-in",
                        date = state.checkInDate,
                        time = "12:00 noon",
                        minDate = LocalDate.now(),
                        modifier = Modifier.fillMaxWidth(),
                        onSelected = { viewModel.setDates(it, state.checkOutDate) }
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    StayDateButton(
                        label = "Check-out",
                        date = state.checkOutDate,
                        time = "11:00 AM",
                        minDate = state.checkInDate.plusDays(1),
                        modifier = Modifier.fillMaxWidth(),
                        onSelected = { viewModel.setDates(state.checkInDate, it) }
                    )
                }
            }
        }
        state.catalog?.unitTypes?.let { types ->
            items(types, key = { it.code }) { type ->
                StayTypeSelector(
                    type = type,
                    quantity = state.quantities[type.code] ?: 0,
                    onMinus = { viewModel.changeQuantity(type.code, -1) },
                    onPlus = { viewModel.changeQuantity(type.code, 1) }
                )
            }
        }
        item {
            Button(
                onClick = { viewModel.checkAvailability(onAvailable) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Check availability")
            }
        }
        state.quote?.takeUnless { it.canFulfill }?.let { quote ->
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (quote.canFulfill) "Available" else "Not available",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (quote.canFulfill) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text("${quote.nightCount} night(s)")
                        quote.unitTypes.filter { (it.requestedQuantity ?: 0) > 0 }.forEach {
                            Text("${it.displayName} × ${it.requestedQuantity}: ₹${(it.lineTotal ?: 0.0).asRupees()}")
                        }
                        HorizontalDivider()
                        Text("Total ₹${quote.totalAmount.asRupees()}", fontWeight = FontWeight.Bold)
                        Text(
                            "Availability is indicative until the Stay Admin confirms your request.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun StayBookingDetailsContent(
    state: StayUiState,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onBack: () -> Unit,
    viewModel: StayViewModel,
    modifier: Modifier = Modifier
) {
    val quote = state.quote
    if (quote == null || !quote.canFulfill) {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Availability must be checked before entering booking details.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack) { Text("Back to Stay") }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your stay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${quote.checkInDate} → ${quote.checkOutDate} · ${quote.nightCount} night(s)")
                    quote.unitTypes.filter { (it.requestedQuantity ?: 0) > 0 }.forEach {
                        Text("${it.displayName} × ${it.requestedQuantity}: ₹${(it.lineTotal ?: 0.0).asRupees()}")
                    }
                    HorizontalDivider()
                    Text("Total ₹${quote.totalAmount.asRupees()}", fontWeight = FontWeight.Bold)
                    Text(
                        "Your reservation will be confirmed by the Admin after your request and payment are reviewed.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            Text("Primary contact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedTextField(
                value = state.contactName,
                onValueChange = { viewModel.updateContact(name = it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = state.contactPhone,
                onValueChange = { viewModel.updateContact(phone = it) },
                label = { Text("Phone") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = state.guestCount,
                onValueChange = { if (it.all(Char::isDigit)) viewModel.updateContact(guests = it) },
                label = { Text("Total guests") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = state.customerNote,
                onValueChange = { viewModel.updateContact(note = it) },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Cancellation policy", fontWeight = FontWeight.Bold)
                    Text("Cancellation may be requested before 12:00 noon check-in. A full refund applies when requested at least 48 hours before check-in; later refunds are at admin discretion. All cancellation requests require admin approval.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.policyAccepted,
                            onCheckedChange = { viewModel.updateContact(accepted = it) }
                        )
                        Text("I have read and accept this policy.")
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    if (isLoggedIn) viewModel.submitBooking() else onRequireLogin()
                },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Submit booking request")
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun MyStaysContent(
    state: StayUiState,
    onBookStay: () -> Unit,
    onCancel: (StayBooking) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading && state.myBookings.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.myBookings.isEmpty() -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("No Stay bookings yet")
                Button(onClick = onBookStay) { Text("Book a Stay") }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.myBookings, key = { it.id }) { booking ->
                    StayBookingCard(booking) {
                        if (booking.status == "pending" || booking.status == "confirmed") {
                            OutlinedButton(onClick = { onCancel(booking) }) {
                                Text("Request cancellation")
                            }
                        }
                    }
                }
                if (state.myBookings.size < state.myBookingsTotal) {
                    item {
                        OutlinedButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load more (${state.myBookings.size}/${state.myBookingsTotal})")
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
internal fun StayTypeSelector(
    type: StayUnitType,
    quantity: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(type.displayName, fontWeight = FontWeight.Bold)
                val capacity = if (type.code == "hall") {
                    type.capacity?.let { "$it guests · " }.orEmpty()
                } else {
                    ""
                }
                Text("$capacity₹${type.nightlyRate.asRupees()}/night")
                type.totalUnits?.let { Text("$it available units", style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = onMinus, enabled = quantity > 0) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(quantity.toString(), fontWeight = FontWeight.Bold)
            IconButton(onClick = onPlus, enabled = quantity < (type.totalUnits ?: 20)) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
internal fun StayBookingCard(
    booking: StayBooking,
    showContactDetails: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(booking.reference, fontWeight = FontWeight.Bold)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    booking.status.replace('_', ' ').uppercase(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
            if (showContactDetails) {
                Text("Name: ${booking.contactName}", fontWeight = FontWeight.Medium)
                Text("Phone: ${booking.contactPhone}")
                if (booking.contactEmail.isNotBlank()) {
                    Text("Email: ${booking.contactEmail}")
                }
            }
            Text("${booking.checkInDate} → ${booking.checkOutDate} · ${booking.nightCount} night(s)")
            Text("${booking.guestCount} guests · ₹${booking.totalAmount.asRupees()}")
            if (booking.items.isNotEmpty()) {
                Text(booking.items.joinToString { "${it.unitTypeName} × ${it.quantity}" })
            }
            booking.rejectionReason?.let { Text("Reason: $it", color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

@Composable
internal fun StayDateButton(
    label: String,
    date: LocalDate,
    time: String,
    minDate: LocalDate,
    modifier: Modifier = Modifier,
    onSelected: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth
            ).apply {
                datePicker.minDate = minDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.show()
        },
        modifier = modifier
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(date.format(DateTimeFormatter.ofPattern("dd MMM yy")))
            Text(time, style = MaterialTheme.typography.labelSmall)
        }
    }
}

internal fun Double.asRupees(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format("%.2f", this)
