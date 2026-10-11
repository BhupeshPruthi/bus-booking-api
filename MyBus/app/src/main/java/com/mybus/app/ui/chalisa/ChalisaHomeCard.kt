package com.mybus.app.ui.chalisa

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.ChalisaSummary
import com.mybus.app.data.repository.ChalisaRepository
import com.mybus.app.ui.chalisa.lockscreen.ChalisaLockScreenCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ChalisaHomeViewModel @Inject constructor(
    private val repository: ChalisaRepository,
    private val lockScreenCounter: ChalisaLockScreenCounter
) : ViewModel() {
    private val _summary = MutableStateFlow<ChalisaSummary?>(null)
    val summary: StateFlow<ChalisaSummary?> = _summary.asStateFlow()

    /** Called each time Home opens, so an ended marathon disappears from the Home page. */
    fun refresh() {
        viewModelScope.launch {
            repository.summary().onSuccess { summary ->
                _summary.value = summary
                // A lock-screen counter left on from an ended marathon is cleared here.
                if (summary.state == "none") lockScreenCounter.onNoMarathonRunning()
            }
        }
    }
}

/**
 * Home page entry for the Hanuman Chalisa marathon. Users see it only while a marathon is
 * scheduled or running; the super admin always sees it, to schedule the next one.
 */
@Composable
fun ChalisaHomeCard(
    canManage: Boolean,
    onOpen: () -> Unit,
    onSchedule: () -> Unit,
    viewModel: ChalisaHomeViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val current = summary
    if (current == null) {
        // Couldn't load: the super admin still gets a way in (the screen shows its own error).
        if (canManage) AdminScheduleCard(null, onOpen)
        return
    }
    val marathon = current.marathon
    when {
        marathon != null && current.state == "running" -> RunningCard(current, onOpen)
        marathon != null && current.state == "scheduled" -> ScheduledCard(current, onOpen)
        canManage -> AdminScheduleCard(current, onSchedule)
        else -> Unit
    }
}

@Composable
private fun SaffronEntry(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun EntryHeader(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RunningCard(summary: ChalisaSummary, onOpen: () -> Unit) {
    SaffronEntry(onClick = onOpen) {
        EntryHeader("Hanuman Chalisa Counter", summary.marathon?.title.orEmpty())
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        Row(Modifier.fillMaxWidth()) {
            Stat(formatCount(summary.total), "Group", Modifier.weight(1f))
            Stat(formatCount(summary.participantCount), "Devotees", Modifier.weight(1f))
            Stat(formatCount(summary.myToday), "You today", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScheduledCard(summary: ChalisaSummary, onOpen: () -> Unit) {
    val marathon = summary.marathon ?: return
    val start = parseInstant(marathon.startsAt)
    val serverNow = parseInstant(summary.serverTime) ?: Instant.now()
    SaffronEntry(onClick = onOpen) {
        EntryHeader(marathon.title, "Hanuman Chalisa · group reading")
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        if (start != null) {
            Text("Starts ${formatStart(start)}", fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(
                formatStartsIn(start, serverNow) + (marathon.goal?.let { " · Goal ${formatCount(it)} readings" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AdminScheduleCard(summary: ChalisaSummary?, onSchedule: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hanuman Chalisa marathon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (summary == null) "Couldn't load · only you can see this" else "No marathon scheduled · only you can see this",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = onSchedule, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        summary == null -> "Open Hanuman Chalisa"
                        summary.lastEnded != null -> "Schedule the next marathon"
                        else -> "Schedule a marathon"
                    }
                )
            }
            summary?.lastEnded?.let {
                Text(
                    "Last: ${it.title} · ${formatCount(it.finalTotal)} readings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
