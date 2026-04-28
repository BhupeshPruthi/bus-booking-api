package com.mybus.app.ui.events

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    onBack: () -> Unit,
    viewModel: AddEventViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(state.error!!) },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
        )
    }

    if (state.created != null) {
        val created = state.created!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccess(); onBack() },
            title = { Text("Event Added") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Header: ${created.header}")
                    Text("Sub Header: ${created.subHeader}")
                    Text("Date: ${formatEventDate(created.eventDate)}")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSuccess(); onBack() }) { Text("Done") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Event", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Text(
                text = "Create an upcoming event for users to see on the Home page.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = state.header,
                onValueChange = { viewModel.updateHeader(it) },
                label = { Text("Header") },
                singleLine = true,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.subHeader,
                onValueChange = { viewModel.updateSubHeader(it) },
                label = { Text("Sub Header") },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            EventDatePicker(
                date = state.date,
                enabled = !state.isLoading,
                onDateSelected = { viewModel.updateDate(it) }
            )

            Button(
                onClick = { viewModel.createEvent() },
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Event, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Event", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun EventDatePicker(
    date: LocalDate?,
    enabled: Boolean,
    onDateSelected: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

    OutlinedButton(
        onClick = {
            val now = date ?: LocalDate.now()
            DatePickerDialog(
                context,
                { _, year, month, day -> onDateSelected(LocalDate.of(year, month + 1, day)) },
                now.year,
                now.monthValue - 1,
                now.dayOfMonth
            ).show()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(date?.format(formatter) ?: "Select Event Date")
    }
}

private fun formatEventDate(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val zoned = instant.atZone(ZoneId.systemDefault())
        zoned.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    } catch (_: Exception) {
        isoString
    }
}

