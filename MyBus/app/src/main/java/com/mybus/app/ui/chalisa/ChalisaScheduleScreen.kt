package com.mybus.app.ui.chalisa

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.ChalisaMarathon
import com.mybus.app.data.repository.ChalisaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ChalisaScheduleUiState(
    val isLoading: Boolean = false,
    val existing: ChalisaMarathon? = null,
    val title: String = "",
    val startNow: Boolean = false,
    val date: LocalDate = LocalDate.now(ChalisaZone).plusDays(1),
    val time: LocalTime = LocalTime.of(6, 0),
    val goalText: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
) {
    val isEditing get() = existing != null
    /** Once a marathon has started, only its name and goal can change. */
    val startLocked get() = existing?.state == "running"
}

@HiltViewModel
class ChalisaScheduleViewModel @Inject constructor(
    private val repository: ChalisaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val marathonId: String? = savedStateHandle.get<String>("marathonId")?.takeIf { it.isNotBlank() }
    private val _uiState = MutableStateFlow(ChalisaScheduleUiState(isLoading = marathonId != null))
    val uiState: StateFlow<ChalisaScheduleUiState> = _uiState.asStateFlow()

    init {
        if (marathonId != null) load(marathonId)
    }

    private fun load(id: String) {
        viewModelScope.launch {
            repository.summary()
                .onSuccess { summary ->
                    val marathon = summary.marathon?.takeIf { it.id == id }
                    if (marathon == null) {
                        _uiState.update { it.copy(isLoading = false, error = "This Path is no longer scheduled or running.") }
                        return@onSuccess
                    }
                    val start = parseInstant(marathon.startsAt)?.atZone(ChalisaZone)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            existing = marathon,
                            title = marathon.title,
                            date = start?.toLocalDate() ?: it.date,
                            time = start?.toLocalTime()?.withSecond(0)?.withNano(0) ?: it.time,
                            goalText = marathon.goal?.toString().orEmpty()
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, error = error.message) } }
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value.take(120), error = null) }
    fun onStartNowChange(value: Boolean) = _uiState.update { it.copy(startNow = value, error = null) }
    fun onDateChange(value: LocalDate) = _uiState.update { it.copy(date = value, error = null) }
    fun onTimeChange(value: LocalTime) = _uiState.update { it.copy(time = value, error = null) }
    fun onGoalChange(value: String) = _uiState.update { it.copy(goalText = value.filter(Char::isDigit).take(9), error = null) }

    fun save() {
        val state = _uiState.value
        val title = state.title.trim()
        if (title.length < 2) {
            _uiState.update { it.copy(error = "Enter a name for the Path.") }
            return
        }
        val goal = state.goalText.toLongOrNull()?.takeIf { it > 0 }
        val start: Instant? = when {
            state.startLocked -> null
            !state.isEditing && state.startNow -> null
            else -> LocalDateTime.of(state.date, state.time).atZone(ChalisaZone).toInstant()
        }
        if (start != null && start.isBefore(Instant.now().minusSeconds(60))) {
            _uiState.update { it.copy(error = "The start time is in the past. Choose a later time, or start right away.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val existing = state.existing
            val result = if (existing == null) {
                repository.schedule(title, start, goal)
            } else {
                repository.update(existing.id, title, start, goal)
            }
            result
                .onSuccess { _uiState.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { error -> _uiState.update { it.copy(isSaving = false, error = error.message) } }
        }
    }
}

private val pickerDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val pickerTimeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChalisaScheduleScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ChalisaScheduleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit Path" else "Schedule a Path", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Path name") },
                placeholder = { Text("Hanuman Jayanti Path") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            if (state.startLocked) {
                Text(
                    "This Path has started, so its start time can't change. You can still edit the name and goal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (!state.isEditing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Start right away", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Counting opens as soon as you save.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = state.startNow, onCheckedChange = viewModel::onStartNowChange)
                    }
                }
                if (state.isEditing || !state.startNow) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day -> viewModel.onDateChange(LocalDate.of(year, month + 1, day)) },
                                    state.date.year,
                                    state.date.monthValue - 1,
                                    state.date.dayOfMonth
                                ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(state.date.format(pickerDateFormat)) }
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute -> viewModel.onTimeChange(LocalTime.of(hour, minute)) },
                                    state.time.hour,
                                    state.time.minute,
                                    false
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(state.time.format(pickerTimeFormat)) }
                    }
                    Text(
                        "Start date and time, India time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = state.goalText,
                onValueChange = viewModel::onGoalChange,
                label = { Text("Goal (optional)") },
                placeholder = { Text("200000") },
                supportingText = {
                    state.goalText.toLongOrNull()?.let { Text("${formatCount(it)} readings") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                if (state.isEditing) "Changes show on everyone's screen the next time they open it."
                else "Everyone sees the Path on their Home page as soon as you save. It runs until you end it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (state.isEditing) "Save changes" else "Schedule Path")
            }
        }
    }
}
