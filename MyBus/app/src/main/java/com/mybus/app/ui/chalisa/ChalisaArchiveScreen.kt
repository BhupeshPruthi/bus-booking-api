package com.mybus.app.ui.chalisa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.ChalisaArchivedMarathon
import com.mybus.app.data.repository.ChalisaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChalisaArchiveUiState(
    val isLoading: Boolean = true,
    val marathons: List<ChalisaArchivedMarathon> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ChalisaArchiveViewModel @Inject constructor(
    private val repository: ChalisaRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChalisaArchiveUiState())
    val uiState: StateFlow<ChalisaArchiveUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.archive()
                .onSuccess { _uiState.value = ChalisaArchiveUiState(isLoading = false, marathons = it) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChalisaArchiveScreen(
    onBack: () -> Unit,
    viewModel: ChalisaArchiveViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::load) { Text("Retry") }
                }
                state.marathons.isEmpty() -> Text(
                    "Finished Paths will appear here.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.marathons, key = { it.id }) { marathon -> ArchivedMarathonCard(marathon) }
                }
            }
        }
    }
}

@Composable
private fun ArchivedMarathonCard(marathon: ChalisaArchivedMarathon) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(marathon.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val start = parseInstant(marathon.startsAt)
            val end = parseInstant(marathon.endedAt)
            Text(
                buildString {
                    if (start != null) append(formatDate(start))
                    if (end != null) append(" – ${formatDate(end)}")
                    marathon.endedBy?.let { append(" · ended by $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("${formatCount(marathon.finalTotal)} readings", modifier = Modifier.weight(1f))
                Text("${formatCount(marathon.finalParticipantCount)} devotees", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
