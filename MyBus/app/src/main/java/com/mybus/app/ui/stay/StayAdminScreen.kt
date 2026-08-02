package com.mybus.app.ui.stay

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.StayBooking
import com.mybus.app.data.remote.dto.CreateStayBookingRequest
import com.mybus.app.data.remote.dto.StayBookingItemRequest
import com.mybus.app.data.remote.dto.StayCancellation
import com.mybus.app.data.remote.dto.StayCoupon
import com.mybus.app.data.remote.dto.StayDailyOccupancyDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class StayAdminPage(val title: String) {
    DASHBOARD("Stay Admin"),
    PENDING("Pending Requests"),
    CONFIRMED("Confirmed Bookings"),
    OCCUPANCY("Daily Occupancy"),
    CANCELLATIONS("Cancellation Requests"),
    COUPONS("Coupons"),
    ARCHIVE("All Stay Records"),
    CREATE_BOOKING("Make Booking")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StayAdminScreen(viewModel: StayAdminViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var page by remember { mutableStateOf(StayAdminPage.DASHBOARD) }
    var menuOpen by remember { mutableStateOf(false) }
    var rejectTarget by remember { mutableStateOf<StayBooking?>(null) }
    var cancellationTarget by remember { mutableStateOf<StayCancellation?>(null) }
    var showCreateCoupon by remember { mutableStateOf(false) }
    var deactivateCoupon by remember { mutableStateOf<StayCoupon?>(null) }
    var adminCancellationTarget by remember { mutableStateOf<StayBooking?>(null) }
    var reason by remember { mutableStateOf("") }

    state.error?.let {
        NoticeDialog("Unable to continue", it, viewModel::clearNotice)
    }
    state.message?.let {
        NoticeDialog("Done", it, viewModel::clearNotice)
    }
    rejectTarget?.let { booking ->
        TextInputDialog(
            title = "Reject ${booking.reference}",
            label = "Mandatory reason",
            value = reason,
            onValueChange = { reason = it.take(1000) },
            onDismiss = { rejectTarget = null },
            onConfirm = {
                viewModel.reject(booking.id, reason.trim())
                rejectTarget = null
                reason = ""
            }
        )
    }
    cancellationTarget?.let { request ->
        CancellationDecisionDialog(
            request,
            onDismiss = { cancellationTarget = null },
            onDecision = { action, refund, amount, decisionReason ->
                viewModel.decideCancellation(
                    request.id,
                    action,
                    refund,
                    amount,
                    decisionReason
                )
                cancellationTarget = null
            }
        )
    }
    if (showCreateCoupon) {
        CreateCouponDialog(
            onDismiss = { showCreateCoupon = false },
            onCreate = { code, amount, startDate, endDate ->
                viewModel.createCoupon(code, amount, startDate, endDate)
                showCreateCoupon = false
            }
        )
    }
    adminCancellationTarget?.let { booking ->
        AdminStayCancellationDialog(
            booking = booking,
            onDismiss = { adminCancellationTarget = null },
            onConfirm = { refundDecision, refundAmount, cancellationReason ->
                viewModel.cancelAdminBooking(
                    booking.id,
                    refundDecision,
                    refundAmount,
                    cancellationReason
                )
                adminCancellationTarget = null
            }
        )
    }
    deactivateCoupon?.let { coupon ->
        AlertDialog(
            onDismissRequest = { deactivateCoupon = null },
            title = { Text("Deactivate ${coupon.code}?") },
            text = { Text("Users will no longer be able to apply this coupon.") },
            dismissButton = {
                TextButton(onClick = { deactivateCoupon = null }) { Text("Back") }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.deactivateCoupon(coupon.id)
                    deactivateCoupon = null
                }) { Text("Deactivate") }
            }
        )
    }

    fun open(target: StayAdminPage) {
        page = target
        when (target) {
            StayAdminPage.PENDING -> viewModel.loadBookings("pending")
            StayAdminPage.CONFIRMED -> viewModel.loadBookings("confirmed")
            StayAdminPage.OCCUPANCY -> viewModel.loadDailyOccupancy()
            StayAdminPage.CANCELLATIONS -> viewModel.loadCancellations()
            StayAdminPage.COUPONS -> viewModel.loadCoupons()
            StayAdminPage.ARCHIVE -> viewModel.loadBookings(null)
            else -> Unit
        }
    }

    BackHandler(enabled = page == StayAdminPage.CREATE_BOOKING) {
        page = StayAdminPage.DASHBOARD
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text(page.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (page != StayAdminPage.DASHBOARD) {
                        IconButton(onClick = { page = StayAdminPage.DASHBOARD }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Stay Admin"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (page == StayAdminPage.DASHBOARD) viewModel.refreshDashboard()
                        else open(page)
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Sections")
                        }
                        DropdownMenu(menuOpen, { menuOpen = false }) {
                            StayAdminPage.entries.forEach { target ->
                                    DropdownMenuItem(
                                        text = { Text(target.title) },
                                        onClick = {
                                            menuOpen = false
                                            open(target)
                                        }
                                    )
                                }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                StayAdminPage.DASHBOARD -> Dashboard(state, ::open) {
                    page = StayAdminPage.CREATE_BOOKING
                }
                StayAdminPage.CREATE_BOOKING -> AdminStayBookingPage(
                    onConfirm = {
                        viewModel.createAdminBooking(it)
                        page = StayAdminPage.DASHBOARD
                    }
                )
                StayAdminPage.PENDING -> BookingPage(
                    state,
                    emptyMessage = "No pending requests",
                    onSearch = { viewModel.loadBookings("pending", it) },
                    onLoadMore = { viewModel.loadBookings("pending", state.bookingSearch, false) }
                ) { booking ->
                    Button(onClick = { viewModel.confirm(booking.id) }) { Text("Confirm") }
                    OutlinedButton(onClick = {
                        reason = ""
                        rejectTarget = booking
                    }) { Text("Reject") }
                }
                StayAdminPage.CONFIRMED -> BookingPage(
                    state,
                    emptyMessage = "No confirmed bookings",
                    onSearch = { viewModel.loadBookings("confirmed", it) },
                    onLoadMore = { viewModel.loadBookings("confirmed", state.bookingSearch, false) }
                ) { booking ->
                    if (booking.bookingSource == "admin") {
                        OutlinedButton(onClick = { adminCancellationTarget = booking }) {
                            Text("Cancel booking")
                        }
                    }
                }
                StayAdminPage.OCCUPANCY -> DailyOccupancyPage(state)
                StayAdminPage.CANCELLATIONS -> CancellationPage(
                    state,
                    onReview = { cancellationTarget = it },
                    onLoadMore = { viewModel.loadCancellations(false) }
                )
                StayAdminPage.COUPONS -> CouponsPage(
                    state = state,
                    onCreate = { showCreateCoupon = true },
                    onDeactivate = { deactivateCoupon = it }
                )
                StayAdminPage.ARCHIVE -> BookingPage(
                    state,
                    emptyMessage = "No Stay records",
                    onSearch = { viewModel.loadBookings(null, it) },
                    onLoadMore = { viewModel.loadBookings(null, state.bookingSearch, false) }
                )
            }
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun NoticeDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value,
                onValueChange,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
        confirmButton = {
            Button(onClick = onConfirm, enabled = value.isNotBlank()) { Text("Confirm") }
        }
    )
}

@Composable
private fun Dashboard(
    state: StayAdminUiState,
    onOpen: (StayAdminPage) -> Unit,
    onCreateBooking: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(onClick = onCreateBooking, modifier = Modifier.fillMaxWidth()) {
                Text("Add confirmed booking")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard("Pending", state.pendingTotal, Modifier.weight(1f)) {
                    onOpen(StayAdminPage.PENDING)
                }
                DashboardCard("Cancellations", state.cancellationTotal, Modifier.weight(1f)) {
                    onOpen(StayAdminPage.CANCELLATIONS)
                }
            }
        }
        item {
            DashboardCard("Confirmed", state.confirmedTotal, Modifier.fillMaxWidth()) {
                onOpen(StayAdminPage.CONFIRMED)
            }
        }
        item {
            DashboardAction(
                "Daily Occupancy",
                "See booked and available rooms for the next 30 days"
            ) { onOpen(StayAdminPage.OCCUPANCY) }
        }
        item { DashboardAction("Coupons", "Create and manage Stay discount codes") { onOpen(StayAdminPage.COUPONS) } }
        item { DashboardAction("All Stay records", "Search the complete retained history") { onOpen(StayAdminPage.ARCHIVE) } }
    }
}

@Composable
private fun DailyOccupancyPage(state: StayAdminUiState) {
    val report = state.dailyOccupancy
    if (report == null && !state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No occupancy information available")
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        report?.let { occupancy ->
            item {
                Text(
                    "${occupancy.fromDate} to ${occupancy.toDate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(occupancy.days, key = { it.date }) { day ->
                DailyOccupancyCard(day)
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun DailyOccupancyCard(day: StayDailyOccupancyDay) {
    val dateLabel = remember(day.date) {
        runCatching {
            LocalDate.parse(day.date)
                .format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"))
        }.getOrDefault(day.date)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${day.bookingCount} ${if (day.bookingCount == 1) "booking" else "bookings"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            day.unitTypes.forEach { unit ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(unit.displayName, modifier = Modifier.weight(1f))
                    Text(
                        "${unit.bookedUnits} booked · ${unit.availableUnits} available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(title: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title)
        }
    }
}

@Composable
private fun DashboardAction(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AdminStayBookingPage(
    modifier: Modifier = Modifier,
    onConfirm: (CreateStayBookingRequest) -> Unit
) {
    var checkInDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var checkOutDate by remember { mutableStateOf(LocalDate.now().plusDays(2)) }
    var threeBed by remember { mutableStateOf("") }
    var fourBed by remember { mutableStateOf("") }
    var fiveBed by remember { mutableStateOf("") }
    var halls by remember { mutableStateOf("") }
    var guestCount by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var coupon by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val quantities = listOf(
        "three_bed_room" to threeBed.toIntOrNull(),
        "four_bed_room" to fourBed.toIntOrNull(),
        "five_bed_room" to fiveBed.toIntOrNull(),
        "hall" to halls.toIntOrNull()
    )
    val selectedItems = quantities.filter { (it.second ?: 0) > 0 }
        .map { StayBookingItemRequest(it.first, it.second!!) }
    val canConfirm = !checkOutDate.isBefore(checkInDate.plusDays(1)) &&
        selectedItems.isNotEmpty() &&
        (guestCount.toIntOrNull() ?: 0) > 0 &&
        name.isNotBlank() && phone.isNotBlank()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "This records payment as received and confirms the booking immediately.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Stay dates and accommodation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                CouponDateButton("Check-in", checkInDate) { selected ->
                    checkInDate = selected
                    if (!checkOutDate.isAfter(selected)) checkOutDate = selected.plusDays(1)
                }
                CouponDateButton("Check-out", checkOutDate) { checkOutDate = it }
                QuantityField("3 Bed rooms", threeBed) { threeBed = it }
                QuantityField("4 Bed rooms", fourBed) { fourBed = it }
                QuantityField("5 Bed rooms", fiveBed) { fiveBed = it }
                QuantityField("Halls", halls) { halls = it }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Guest details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = guestCount,
                    onValueChange = { if (it.all(Char::isDigit)) guestCount = it },
                    label = { Text("Total guests") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(name, { name = it.take(200) }, label = { Text("Guest name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it.take(20) }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(coupon, { coupon = it.uppercase().take(50) }, label = { Text("Coupon code (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(1000) }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(
                onClick = {
                    onConfirm(
                        CreateStayBookingRequest(
                            checkInDate = checkInDate.toString(),
                            checkOutDate = checkOutDate.toString(),
                            items = selectedItems,
                            guestCount = guestCount.toInt(),
                            contactName = name.trim(),
                            // The ViewModel supplies the signed-in admin's email, as the
                            // normal Stay flow does; no email needs to be entered here.
                            contactEmail = "",
                            contactPhone = phone.trim(),
                            couponCode = coupon.trim().ifBlank { null },
                            customerNote = note.trim().ifBlank { null }
                        )
                    )
                },
                enabled = canConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) { Text("Confirm booking") }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun QuantityField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all(Char::isDigit)) onChange(it.take(2)) },
        label = { Text(label) },
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AdminStayCancellationDialog(
    booking: StayBooking,
    onDismiss: () -> Unit,
    onConfirm: (String, Double?, String?) -> Unit
) {
    var decision by remember(booking.id) { mutableStateOf("full") }
    var amount by remember(booking.id) { mutableStateOf("") }
    var reason by remember(booking.id) { mutableStateOf("") }
    val partialAmount = amount.toDoubleOrNull()
    val canConfirm = when (decision) {
        "partial" -> (partialAmount ?: 0.0) > 0 && reason.isNotBlank()
        "none" -> reason.isNotBlank()
        else -> true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel ${booking.reference}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose the refund to record. The server enforces the 48-hour refund policy.")
                Row {
                    listOf("full", "partial", "none").forEach { option ->
                        FilterChip(
                            selected = decision == option,
                            onClick = { decision = option },
                            label = { Text(option.replaceFirstChar(Char::uppercase)) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                if (decision == "partial") {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it },
                        label = { Text("Refund amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(1000) },
                    label = { Text(if (decision == "full") "Note (optional)" else "Reason") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
        confirmButton = {
            Button(
                onClick = { onConfirm(decision, partialAmount, reason.trim().ifBlank { null }) },
                enabled = canConfirm
            ) { Text("Cancel booking") }
        }
    )
}

@Composable
private fun CouponsPage(
    state: StayAdminUiState,
    onCreate: () -> Unit,
    onDeactivate: (StayCoupon) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text("Create coupon")
            }
        }
        if (state.coupons.isEmpty() && !state.isLoading) {
            item { Text("No coupons created yet.") }
        } else if (state.coupons.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        state.coupons.forEachIndexed { index, coupon ->
                            CouponRow(coupon, onDeactivate)
                            if (index != state.coupons.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun CouponRow(coupon: StayCoupon, onDeactivate: (StayCoupon) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                coupon.code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = if (coupon.status == "active") {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    coupon.status.replaceFirstChar(Char::uppercase),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Text("₹${coupon.discountAmount.asRupees()} discount")
        Text(
            "${coupon.startDate} to ${coupon.endDate}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (coupon.isActive && coupon.status != "expired") {
            OutlinedButton(onClick = { onDeactivate(coupon) }) {
                Text("Deactivate")
            }
        }
    }
}

@Composable
private fun CreateCouponDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Double, String, String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusDays(30)) }
    val parsedAmount = amount.toDoubleOrNull()
    val canCreate = code.length >= 3 && (parsedAmount ?: 0.0) > 0 && !endDate.isBefore(startDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Stay coupon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { value ->
                        code = value.uppercase()
                            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
                            .take(50)
                    },
                    label = { Text("Coupon code") },
                    supportingText = { Text("Users will type this code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it
                    },
                    label = { Text("Discount amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CouponDateButton("Starts", startDate) { selected ->
                    startDate = selected
                    if (endDate.isBefore(selected)) endDate = selected
                }
                CouponDateButton("Ends", endDate) { endDate = it }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(code, parsedAmount!!, startDate.toString(), endDate.toString())
                },
                enabled = canCreate
            ) { Text("Create") }
        }
    )
}

@Composable
private fun CouponDateButton(label: String, date: LocalDate, onSelected: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth
            ).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("$label: ${date.format(DateTimeFormatter.ofPattern("dd MMM yy"))}")
    }
}

@Composable
private fun BookingPage(
    state: StayAdminUiState,
    emptyMessage: String,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    actions: @Composable RowScope.(StayBooking) -> Unit = {}
) {
    var search by remember(state.bookingStatus) { mutableStateOf(state.bookingSearch) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                search,
                { search = it.take(200) },
                label = { Text("Reference, guest, phone, or email") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onSearch(search) }) { Text("Search") }
        }
        if (state.bookings.isEmpty() && !state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(emptyMessage) }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.bookings, key = { it.id }) { booking ->
                    StayBookingCard(booking, showContactDetails = true) { actions(booking) }
                }
                if (state.bookings.size < state.bookingTotal) {
                    item {
                        OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                            Text("Load more (${state.bookings.size}/${state.bookingTotal})")
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun CancellationPage(
    state: StayAdminUiState,
    onReview: (StayCancellation) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.cancellations, key = { it.id }) { request ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(request.reference.orEmpty(), fontWeight = FontWeight.Bold)
                    Text("${request.contactName.orEmpty()} · ${request.contactPhone.orEmpty()}")
                    Text("Original state: ${request.previousBookingStatus.replace('_', ' ')}")
                    Text(
                        if (request.standardFullRefundEligible) {
                            "Full refund required"
                        } else {
                            "Refund at admin discretion"
                        }
                    )
                    request.reason?.let { Text("Reason: $it") }
                    Button(onClick = { onReview(request) }) { Text("Review") }
                }
            }
        }
        if (state.cancellations.size < state.cancellationTotal) {
            item {
                OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("Load more")
                }
            }
        }
    }
}

@Composable
private fun CancellationDecisionDialog(
    request: StayCancellation,
    onDismiss: () -> Unit,
    onDecision: (String, String?, Double?, String?) -> Unit
) {
    var decision by remember(request.id) {
        mutableStateOf(if (request.standardFullRefundEligible) "full" else "none")
    }
    var amount by remember(request.id) { mutableStateOf("") }
    var reason by remember(request.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancellation decision") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (request.standardFullRefundEligible) {
                    Text("This request is entitled to a full refund.")
                } else {
                    Row {
                        listOf("none", "partial", "full").forEach { option ->
                            FilterChip(
                                selected = decision == option,
                                onClick = { decision = option },
                                label = { Text(option.replaceFirstChar(Char::uppercase)) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
                if (decision == "partial") {
                    OutlinedTextField(
                        amount,
                        { if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it },
                        label = { Text("Refund amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                OutlinedTextField(
                    reason,
                    { reason = it.take(1000) },
                    label = { Text("Decision note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (reason.isNotBlank()) onDecision("reject", null, null, reason)
                },
                enabled = reason.isNotBlank()
            ) { Text("Reject request") }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDecision(
                        "approve",
                        decision,
                        amount.toDoubleOrNull(),
                        reason.ifBlank { null }
                    )
                },
                enabled = decision != "partial" || (amount.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Approve cancellation") }
        }
    )
}
