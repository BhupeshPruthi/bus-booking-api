package com.mybus.app.ui.pooja

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    onPoojaClick: (poojaId: String) -> Unit,
    onBookPoojaClick: (poojaId: String) -> Unit,
    onMyTokensClick: () -> Unit
) {
    val listViewModel: PoojaListViewModel = hiltViewModel()
    val listState by listViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        listViewModel.loadPoojas()
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
        },
        floatingActionButton = {
            if (!isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (isLoggedIn) {
                            onMyTokensClick()
                        } else {
                            onRequireLogin()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = {
                        Icon(Icons.Filled.History, contentDescription = null)
                    },
                    text = { Text("My Tokens") }
                )
            }
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
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = if (isAdmin) 16.dp else 96.dp
                        ),
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
                                            onBookPoojaClick(pooja.id)
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
                val bookingStatus = poojaBookingStatus(
                    scheduledAt = pooja.scheduledAt,
                    availableTokens = pooja.availableTokens,
                    serverStatus = pooja.bookingStatus
                )
                val canSchedule = canBookPooja(
                    scheduledAt = pooja.scheduledAt,
                    availableTokens = pooja.availableTokens,
                    serverCanBook = pooja.canBook,
                    serverStatus = pooja.bookingStatus
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = { onScheduleClick?.invoke() },
                        enabled = canSchedule && onScheduleClick != null
                    ) {
                        Text(
                            when (bookingStatus) {
                                POOJA_BOOKING_OPEN -> "Book"
                                POOJA_BOOKING_NOT_STARTED -> "Not Started"
                                POOJA_BOOKING_FULL -> "Full"
                                else -> "Expired"
                            }
                        )
                    }
                }
            }
        }
    }
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
