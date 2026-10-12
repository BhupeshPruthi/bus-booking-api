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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.ChalisaMyStats
import com.mybus.app.data.repository.ChalisaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class ChalisaProfileViewModel @Inject constructor(
    private val repository: ChalisaRepository
) : ViewModel() {
    private val _stats = MutableStateFlow<ChalisaMyStats?>(null)
    val stats: StateFlow<ChalisaMyStats?> = _stats.asStateFlow()

    fun refresh() {
        viewModelScope.launch { repository.myStats().onSuccess { _stats.value = it } }
    }
}

/** Lifetime Hanuman Chalisa readings and each marathon's part. Shown even between marathons. */
@Composable
fun ChalisaProfileSection(
    onOpen: () -> Unit,
    viewModel: ChalisaProfileViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val me = stats ?: return
    if (me.participant == null && me.lifetime == 0L) return

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Your Hanuman Chalisa", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open your history")
            }
            Text(formatCount(me.lifetime), fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(
                me.firstReadDate
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?.let { "lifetime readings since ${formatDate(it.atStartOfDay().toInstant(ZoneOffset.ofHoursMinutes(5, 30)))}" }
                    ?: "lifetime readings",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(formatCount(me.today), "Today", Modifier.weight(1f))
                StatTile(formatCount(me.thisMonth), "This month", Modifier.weight(1f))
                StatTile(me.streakDays.toString(), "Day streak", Modifier.weight(1f))
            }
            if (me.marathons.isNotEmpty()) {
                Text("Your Paths", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                me.marathons.take(5).forEach { marathon ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "${marathon.title} · rank ${marathon.rank}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(formatCount(marathon.count), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
