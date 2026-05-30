package com.mybus.app.ui.pooja

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DEFAULT_POOJA_CITY = "Delhi - NCR"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoojaBookingScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: PoojaBookingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Pooja", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isBooking) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.bookingSuccess != null -> {
                BookingSuccessPage(
                    booking = state.bookingSuccess!!,
                    pooja = state.pooja,
                    onDone = onDone,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            state.isLoading && state.pooja == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.pooja == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.error ?: "Failed to load pooja",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.load() }) { Text("Retry") }
                }
            }

            else -> {
                BookingFormPage(
                    state = state,
                    onNameChange = viewModel::updateName,
                    onPhoneChange = viewModel::updatePhone,
                    onMemberCountChange = viewModel::updateMemberCount,
                    onCityChange = viewModel::updateCity,
                    onClearError = viewModel::clearError,
                    onSubmit = viewModel::bookToken,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookingFormPage(
    state: PoojaBookingUiState,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onMemberCountChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onClearError: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pooja = state.pooja ?: return
    var cityPrefillCleared by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PoojaBookingSummary(pooja)
        }

        item {
            Text(
                text = "Booking Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                enabled = !state.isBooking,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Words
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnFocus()
            )
        }

        item {
            OutlinedTextField(
                value = state.phone,
                onValueChange = onPhoneChange,
                label = { Text("Phone") },
                singleLine = true,
                enabled = !state.isBooking,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnFocus()
            )
        }

        item {
            OutlinedTextField(
                value = state.memberCount,
                onValueChange = onMemberCountChange,
                label = { Text("Members") },
                supportingText = { Text("1 token will be issued for this booking request") },
                singleLine = true,
                enabled = !state.isBooking,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnFocus()
            )
        }

        item {
            OutlinedTextField(
                value = state.city,
                onValueChange = { raw -> onCityChange(raw.take(100)) },
                label = { Text("City") },
                singleLine = true,
                enabled = !state.isBooking,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Words
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnFocus { focusState ->
                        if (
                            focusState.isFocused &&
                            !cityPrefillCleared &&
                            state.city == DEFAULT_POOJA_CITY
                        ) {
                            cityPrefillCleared = true
                            onCityChange("")
                        }
                    }
            )
        }

        state.error?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                LaunchedClearError(onClearError)
            }
        }

        item {
            Button(
                onClick = onSubmit,
                enabled = !state.isBooking && pooja.availableTokens > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .bringIntoViewOnFocus()
            ) {
                if (state.isBooking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (pooja.availableTokens > 0) "Book Token" else "No Tokens Available")
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.bringIntoViewOnFocus(
    onFocusChanged: (FocusState) -> Unit = {}
): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return bringIntoViewRequester(requester)
        .onFocusChanged { focusState ->
            onFocusChanged(focusState)
            if (focusState.isFocused) {
                scope.launch {
                    delay(250)
                    requester.bringIntoView()
                }
            }
        }
}

@Composable
private fun LaunchedClearError(onClearError: () -> Unit) {
    TextButton(onClick = onClearError, contentPadding = PaddingValues(0.dp)) {
        Text("Dismiss")
    }
}

@Composable
private fun PoojaBookingSummary(pooja: PoojaDetailData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = pooja.place,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ConfirmationNumber,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${pooja.availableTokens} available / ${pooja.totalTokens} total tokens",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (pooja.availableTokens > 0)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun BookingSuccessPage(
    booking: PoojaBookingData,
    pooja: PoojaDetailData?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        }

        item {
            Text(
                text = "Token Booked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        item {
            Text(
                text = booking.tokenNumber?.let { "Token #$it" } ?: "Token number pending",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pooja?.let {
                        DetailLine("Pooja", it.place)
                        DetailLine("Date", formatDateTime(it.scheduledAt))
                    }
                    DetailLine("Name", booking.name)
                    DetailLine("Phone", booking.phone)
                    DetailLine("Members", booking.memberCount.toString())
                    DetailLine("City", booking.city)
                    DetailLine("Status", booking.status.replaceFirstChar { it.uppercase() })
                }
            }
        }

        item {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
        )
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
