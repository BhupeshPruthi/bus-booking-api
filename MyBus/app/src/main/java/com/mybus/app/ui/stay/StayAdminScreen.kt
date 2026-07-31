package com.mybus.app.ui.stay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.AdminUser
import com.mybus.app.data.remote.dto.StayBooking
import com.mybus.app.data.remote.dto.StayCancellation

private enum class StayAdminPage(val title: String) {
    DASHBOARD("Stay Admin"),
    PENDING("Pending Requests"),
    CONFIRMED("Confirmed Bookings"),
    CANCELLATIONS("Cancellation Requests"),
    ARCHIVE("All Stay Records"),
    RATES("Prices"),
    ADMINS("Admin Access")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StayAdminScreen(
    canManageAdmins: Boolean,
    viewModel: StayAdminViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var page by remember { mutableStateOf(StayAdminPage.DASHBOARD) }
    var menuOpen by remember { mutableStateOf(false) }
    var rejectTarget by remember { mutableStateOf<StayBooking?>(null) }
    var cancellationTarget by remember { mutableStateOf<StayCancellation?>(null) }
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

    fun open(target: StayAdminPage) {
        page = target
        when (target) {
            StayAdminPage.PENDING -> viewModel.loadBookings("pending")
            StayAdminPage.CONFIRMED -> viewModel.loadBookings("confirmed")
            StayAdminPage.CANCELLATIONS -> viewModel.loadCancellations()
            StayAdminPage.ARCHIVE -> viewModel.loadBookings(null)
            StayAdminPage.ADMINS -> if (canManageAdmins) viewModel.loadAdminUsers()
            else -> Unit
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text(page.title, fontWeight = FontWeight.Bold) },
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
                            StayAdminPage.entries
                                .filter { it != StayAdminPage.ADMINS || canManageAdmins }
                                .forEach { target ->
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
                StayAdminPage.DASHBOARD -> Dashboard(state, ::open, canManageAdmins)
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
                )
                StayAdminPage.CANCELLATIONS -> CancellationPage(
                    state,
                    onReview = { cancellationTarget = it },
                    onLoadMore = { viewModel.loadCancellations(false) }
                )
                StayAdminPage.ARCHIVE -> BookingPage(
                    state,
                    emptyMessage = "No Stay records",
                    onSearch = { viewModel.loadBookings(null, it) },
                    onLoadMore = { viewModel.loadBookings(null, state.bookingSearch, false) }
                )
                StayAdminPage.RATES -> RatePage(state, viewModel::updateRate)
                StayAdminPage.ADMINS -> AdminUsersPage(
                    state,
                    viewModel::updateAdminType,
                    onLoadMore = { viewModel.loadAdminUsers(false) }
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
    canManageAdmins: Boolean
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
        item { DashboardAction("All Stay records", "Search the complete retained history") { onOpen(StayAdminPage.ARCHIVE) } }
        item { DashboardAction("Prices", "Update prices for new requests") { onOpen(StayAdminPage.RATES) } }
        if (canManageAdmins) {
            item { DashboardAction("Admin access", "Assign Bus, Stay, or Super Admin") { onOpen(StayAdminPage.ADMINS) } }
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

@Composable
private fun RatePage(
    state: StayAdminUiState,
    onUpdate: (String, Double, String?) -> Unit
) {
    var target by remember { mutableStateOf<com.mybus.app.data.remote.dto.StayUnitType?>(null) }
    var rate by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    target?.let { type ->
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("Update ${type.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        rate,
                        { if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) rate = it },
                        label = { Text("New nightly price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(note, { note = it.take(500) }, label = { Text("Change note (optional)") })
                    Text("Existing requests keep their original quoted price.")
                }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Back") } },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdate(type.id, rate.toDouble(), note.ifBlank { null })
                        target = null
                    },
                    enabled = (rate.toDoubleOrNull() ?: 0.0) > 0
                ) { Text("Save") }
            }
        )
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(state.catalog?.unitTypes.orEmpty(), key = { it.id }) { type ->
            ListItem(
                headlineContent = { Text(type.displayName) },
                supportingContent = { Text("${type.totalUnits} available in inventory") },
                trailingContent = {
                    TextButton(onClick = {
                        target = type
                        rate = type.nightlyRate.asRupees()
                        note = ""
                    }) { Text("₹${type.nightlyRate.asRupees()}") }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun AdminUsersPage(
    state: StayAdminUiState,
    onUpdate: (String, String, String) -> Unit,
    onLoadMore: () -> Unit
) {
    var target by remember { mutableStateOf<AdminUser?>(null) }
    var selectedType by remember { mutableStateOf("consumer") }
    var reason by remember { mutableStateOf("") }
    target?.let { user ->
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("Admin access for ${user.name ?: user.email ?: user.mobile}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "consumer" to "Consumer",
                        "bus_admin" to "Bus Admin",
                        "stay_admin" to "Stay Admin",
                        "super_admin" to "Super Admin"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = selectedType == value,
                            onClick = { selectedType = value },
                            label = { Text(label) }
                        )
                    }
                    OutlinedTextField(
                        reason,
                        { reason = it.take(1000) },
                        label = { Text("Mandatory reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Back") } },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdate(user.id, selectedType, reason.trim())
                        target = null
                    },
                    enabled = reason.isNotBlank()
                ) { Text("Update") }
            }
        )
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(state.adminUsers, key = { it.id }) { user ->
            ListItem(
                headlineContent = { Text(user.name ?: user.email ?: user.mobile.orEmpty()) },
                supportingContent = { Text(user.adminType ?: "Consumer") },
                trailingContent = {
                    TextButton(onClick = {
                        target = user
                        selectedType = user.adminType ?: "consumer"
                        reason = ""
                    }) { Text("Change") }
                }
            )
            HorizontalDivider()
        }
        if (state.adminUsers.size < state.adminUserTotal) {
            item { OutlinedButton(onClick = onLoadMore) { Text("Load more") } }
        }
    }
}
