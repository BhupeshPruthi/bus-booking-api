package com.mybus.app.ui.pooja

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.PoojaBookingData
import com.mybus.app.data.remote.dto.PoojaDetailData
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DEFAULT_POOJA_CITY = "Delhi - NCR"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoojaDetailScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    viewModel: PoojaDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var bookingToCancel by remember { mutableStateOf<PoojaBookingData?>(null) }

    LaunchedEffect(isAdmin) {
        viewModel.load(isAdmin)
    }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(state.error!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }

    if (state.bookingSuccess != null) {
        val booking = state.bookingSuccess!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissBookingSuccess() },
            title = { Text("Token Booked") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    booking.tokenNumber?.let { Text("Token: #$it") }
                    Text("Name: ${booking.name}")
                    Text("Phone: ${booking.phone}")
                    Text("Members: ${booking.memberCount}")
                    Text("City: ${booking.city}")
                    Text("Status: ${booking.status.replaceFirstChar { it.uppercase() }}")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissBookingSuccess() }) { Text("OK") }
            }
        )
    }

    if (state.showBookingDialog) {
        BookingDialog(
            name = state.bookingName,
            phone = state.bookingPhone,
            memberCount = state.bookingMemberCount,
            city = state.bookingCity,
            availableTokens = state.pooja?.availableTokens,
            isLoading = state.bookingLoading,
            onNameChange = { viewModel.updateBookingName(it) },
            onPhoneChange = { viewModel.updateBookingPhone(it) },
            onMemberCountChange = { viewModel.updateBookingMemberCount(it) },
            onCityChange = { viewModel.updateBookingCity(it) },
            onDismiss = { viewModel.closeBookingDialog() },
            onConfirm = { viewModel.bookToken() }
        )
    }

    bookingToCancel?.let { booking ->
        val isCancelling = state.cancelInProgressId == booking.id
        AlertDialog(
            onDismissRequest = { if (!isCancelling) bookingToCancel = null },
            title = { Text("Cancel Token") },
            text = {
                Text(
                    "Cancel token ${booking.tokenNumber?.let { "#$it" } ?: booking.name}? " +
                        "This will release this pooja token for another booking request."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelBookingAsAdmin(booking.id)
                        bookingToCancel = null
                    },
                    enabled = !isCancelling,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Cancel Token")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { bookingToCancel = null }, enabled = !isCancelling) {
                    Text("Keep")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pooja Details", fontWeight = FontWeight.Bold) },
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
            when {
                state.isLoading && state.pooja == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.pooja == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Failed to load pooja",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.load() }) { Text("Retry") }
                    }
                }

                else -> {
                    val pooja = state.pooja!!
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            PoojaSummaryCard(pooja)
                        }

                        if (!isAdmin) {
                            item {
                                Button(
                                    onClick = { viewModel.openBookingDialog() },
                                    enabled = pooja.availableTokens > 0 && !state.bookingLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text(
                                        text = if (pooja.availableTokens > 0) "Book Token" else "No Tokens Available",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            item {
                                Text(
                                    text = "Enrolled (${pooja.bookings?.size ?: 0})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val bookings = pooja.bookings ?: emptyList()
                            if (bookings.isEmpty()) {
                                item {
                                    Text(
                                        text = "No one has enrolled yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                val canCancelTokens = isFutureDateTime(pooja.scheduledAt)
                                items(bookings, key = { it.id }) { booking ->
                                    BookingRow(
                                        booking = booking,
                                        canCancel = canCancelTokens && booking.status == "confirmed",
                                        isCancelling = state.cancelInProgressId == booking.id,
                                        onCancel = { bookingToCancel = booking }
                                    )
                                }
                            }
                        }

                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoojaSummaryCard(pooja: PoojaDetailData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = pooja.place,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatDateTime(pooja.scheduledAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ConfirmationNumber,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tokens: ${pooja.availableTokens} available / ${pooja.totalTokens} total",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (pooja.availableTokens > 0)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Status: ${pooja.status.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BookingRow(
    booking: PoojaBookingData,
    canCancel: Boolean,
    isCancelling: Boolean,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    booking.tokenNumber?.let {
                        Text(
                            text = "Token #$it",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            text = booking.status.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = booking.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Members: ${booking.memberCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "City: ${booking.city}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            booking.createdAt?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Booked: ${formatDateTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            booking.cancelledAt?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Cancelled: ${formatDateTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (canCancel) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isCancelling,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Cancel Token")
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingDialog(
    name: String,
    phone: String,
    memberCount: String,
    city: String,
    availableTokens: Int?,
    isLoading: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onMemberCountChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var cityPrefillCleared by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Book Token") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { raw ->
                        val filtered = raw
                            .filter { it.isLetter() || it.isWhitespace() }
                            .take(50)
                        onNameChange(filtered)
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Words
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 15 && it.all { c -> c.isDigit() }) onPhoneChange(it) },
                    label = { Text("Phone") },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = memberCount,
                    onValueChange = { raw ->
                        val filtered = raw
                            .filter { it.isDigit() }
                            .take(2)
                        onMemberCountChange(filtered)
                    },
                    label = { Text("Members") },
                    supportingText = availableTokens?.let { { Text("Available tokens: $it") } },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { raw -> onCityChange(raw.take(100)) },
                    label = { Text("City") },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Words
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (
                                focusState.isFocused &&
                                !cityPrefillCleared &&
                                city == DEFAULT_POOJA_CITY
                            ) {
                                cityPrefillCleared = true
                                onCityChange("")
                            }
                        }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Book")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") }
        }
    )
}

private fun isFutureDateTime(isoString: String): Boolean {
    return try {
        Instant.parse(isoString).isAfter(Instant.now())
    } catch (_: Exception) {
        false
    }
}

private fun formatDateTime(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val zoned = instant.atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
        zoned.format(formatter)
    } catch (_: Exception) {
        isoString
    }
}
