package com.mybus.app.ui.pooja

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.PoojaListItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoojaTab(
    isAdmin: Boolean,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onAddClick: () -> Unit,
    onPoojaClick: (poojaId: String) -> Unit
) {
    val listViewModel: PoojaListViewModel = hiltViewModel()
    val listState by listViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        listViewModel.loadPoojas()
    }

    if (listState.bookingSuccess != null) {
        val booking = listState.bookingSuccess!!
        AlertDialog(
            onDismissRequest = { listViewModel.dismissBookingSuccess() },
            title = { Text("Token Booked") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Name: ${booking.name}")
                    Text("Phone: ${booking.phone}")
                    Text("Status: ${booking.status.replaceFirstChar { it.uppercase() }}")
                }
            },
            confirmButton = {
                TextButton(onClick = { listViewModel.dismissBookingSuccess() }) { Text("OK") }
            }
        )
    }

    if (listState.showBookingDialog && listState.bookingPooja != null) {
        QuickBookingDialog(
            pooja = listState.bookingPooja!!,
            name = listState.bookingName,
            phone = listState.bookingPhone,
            isLoading = listState.bookingLoading,
            error = listState.bookingError,
            onNameChange = { listViewModel.updateBookingName(it) },
            onPhoneChange = { listViewModel.updateBookingPhone(it) },
            onDismiss = { listViewModel.closeBookingDialog() },
            onConfirm = { listViewModel.bookSelectedPoojaToken() }
        )
    }

    if (listState.bookingError != null && !listState.showBookingDialog) {
        AlertDialog(
            onDismissRequest = { listViewModel.clearBookingError() },
            title = { Text("Error") },
            text = { Text(listState.bookingError!!) },
            confirmButton = {
                TextButton(onClick = { listViewModel.clearBookingError() }) { Text("OK") }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Pooja", fontWeight = FontWeight.Bold) },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = onAddClick) {
                            Icon(Icons.Filled.Add, contentDescription = "Add")
                        }
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
                listState.isLoading && listState.poojas.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                listState.error != null && listState.poojas.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = listState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { listViewModel.loadPoojas() }) { Text("Retry") }
                    }
                }
                !listState.isLoading && listState.poojas.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SelfImprovement,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No upcoming pooja found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(listState.poojas, key = { it.id }) { pooja ->
                            PoojaCard(
                                pooja = pooja,
                                showScheduleButton = !isAdmin,
                                onCardClick = if (isAdmin) {
                                    {
                                        Log.d("PoojaUI", "Pooja card clicked id=${pooja.id}")
                                        onPoojaClick(pooja.id)
                                    }
                                } else null,
                                onScheduleClick = if (!isAdmin) {
                                    {
                                        Log.d("PoojaUI", "Pooja schedule clicked id=${pooja.id}")
                                        if (!isLoggedIn) {
                                            onRequireLogin()
                                        } else {
                                            listViewModel.openBookingDialog(pooja)
                                        }
                                    }
                                } else null
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoojaCard(
    pooja: PoojaListItem,
    showScheduleButton: Boolean,
    onCardClick: (() -> Unit)?,
    onScheduleClick: (() -> Unit)?
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .let { base ->
            if (onCardClick != null) base.clickable(onClick = onCardClick) else base
        }

    Card(
        modifier = cardModifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = pooja.place,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatDateTime(pooja.scheduledAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.ConfirmationNumber,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${pooja.availableTokens} / ${pooja.totalTokens} tokens",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pooja.availableTokens > 0)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
                if (!showScheduleButton) {
                    AssistChip(
                        onClick = onCardClick ?: {},
                        enabled = onCardClick != null,
                        label = {
                            Text(
                                text = pooja.status.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            if (showScheduleButton) {
                Spacer(Modifier.height(12.dp))
                val canSchedule = pooja.availableTokens > 0
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = { onScheduleClick?.invoke() },
                        enabled = canSchedule && onScheduleClick != null
                    ) {
                        Text(if (canSchedule) "Book" else "Full")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickBookingDialog(
    pooja: PoojaListItem,
    name: String,
    phone: String,
    isLoading: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Book Pooja") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${pooja.place} • ${formatDateTime(pooja.scheduledAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                    onValueChange = { raw ->
                        val filtered = raw
                            .filter { it.isDigit() }
                            .take(15)
                        onPhoneChange(filtered)
                    },
                    label = { Text("Phone") },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Confirm")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") }
        }
    )
}

private fun formatDateTime(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val zoned = instant.atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a")
        zoned.format(formatter)
    } catch (_: Exception) {
        isoString
    }
}

