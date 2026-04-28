package com.mybus.app.ui.pooja

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePoojaScreen(
    onBack: () -> Unit,
    viewModel: SchedulePoojaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(uiState.error!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }

    val scrollState = rememberScrollState()

    if (uiState.createdPooja != null) {
        val pooja = uiState.createdPooja!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccess(); onBack() },
            title = { Text("Pooja Scheduled") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Place: ${pooja.place}")
                    Text("Date & Time: ${formatDateTime(pooja.scheduledAt)}")
                    Text("Tokens: ${pooja.totalTokens}")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSuccess(); onBack() }) { Text("Done") }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Schedule Pooja", fontWeight = FontWeight.Bold) },
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
                .imePadding()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Text(
                text = "Create a new pooja schedule for users to book tokens.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionLabel("Date & Time")
            DateTimePicker(
                date = uiState.date,
                time = uiState.time,
                enabled = !uiState.isLoading,
                onDateSelected = { viewModel.updateDate(it) },
                onTimeSelected = { viewModel.updateTime(it) }
            )

            SectionLabel("Details")
            OutlinedTextField(
                value = uiState.place,
                onValueChange = { viewModel.updatePlace(it) },
                label = { Text("Place") },
                singleLine = true,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.totalTokens,
                onValueChange = {
                    if (it.all { c -> c.isDigit() } && it.length <= 5) {
                        viewModel.updateTotalTokens(it)
                    }
                },
                label = { Text("Tokens") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { viewModel.createPooja() },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Schedule Pooja", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun DateTimePicker(
    date: LocalDate?,
    time: LocalTime?,
    enabled: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (LocalTime) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    val label = when {
        date != null && time != null ->
            "${date.format(dateFormatter)} · ${time.format(timeFormatter)}"
        else -> "Select date & time"
    }

    OutlinedButton(
        onClick = {
            val initialDate = date ?: LocalDate.now()
            val initialTime = time ?: LocalTime.of(9, 0)
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val pickedDate = LocalDate.of(year, month + 1, day)
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            onDateSelected(pickedDate)
                            onTimeSelected(LocalTime.of(hour, minute))
                        },
                        initialTime.hour,
                        initialTime.minute,
                        false
                    ).show()
                },
                initialDate.year,
                initialDate.monthValue - 1,
                initialDate.dayOfMonth
            ).show()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}

private fun formatDateTime(isoString: String): String {
    return try {
        val instant = java.time.Instant.parse(isoString)
        val zoned = instant.atZone(java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
        zoned.format(formatter)
    } catch (_: Exception) {
        isoString
    }
}

