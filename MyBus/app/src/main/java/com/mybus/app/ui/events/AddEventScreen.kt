package com.mybus.app.ui.events

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    onBack: () -> Unit,
    viewModel: AddEventViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let { viewModel.updateImage(it) } }
    )

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
                    Text("Photo: ${if (created.imageUrl.isNullOrBlank()) "Default banner" else "Uploaded"}")
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
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnFocus()
            )

            OutlinedTextField(
                value = state.subHeader,
                onValueChange = { viewModel.updateSubHeader(it) },
                label = { Text("Sub Header") },
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnFocus(),
                minLines = 2
            )

            EventDatePicker(
                date = state.date,
                enabled = !state.isLoading,
                onDateSelected = { viewModel.updateDate(it) }
            )

            EventPhotoPicker(
                selectedImageUri = state.imageUri,
                enabled = !state.isLoading,
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemoveImage = { viewModel.updateImage(null) }
            )

            Button(
                onClick = { viewModel.createEvent() },
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .bringIntoViewOnFocus()
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
private fun EventPhotoPicker(
    selectedImageUri: Uri?,
    enabled: Boolean,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Event Photo",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        if (selectedImageUri != null) {
            AsyncImage(
                model = selectedImageUri,
                contentDescription = "Selected event photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1600f / 1066f)
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1600f / 1066f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Default banner will be used",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onPickImage,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (selectedImageUri == null) "Select Photo" else "Change Photo")
            }
            if (selectedImageUri != null) {
                OutlinedButton(
                    onClick = onRemoveImage,
                    enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove")
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
