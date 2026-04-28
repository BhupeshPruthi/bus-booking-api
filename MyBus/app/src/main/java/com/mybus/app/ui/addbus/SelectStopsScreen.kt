package com.mybus.app.ui.addbus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mybus.app.ui.components.StopListTimeline
import com.mybus.app.ui.components.StopTimelineDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectStopsScreen(
    stops: List<PredefinedStop>,
    onToggle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val selectedCount = stops.count { it.selected }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Select Stops") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$selectedCount of ${stops.size} stops selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onBack) {
                        Text("Done")
                    }
                }
            }
        }
    ) { innerPadding ->
        val scroll = rememberScrollState()
        StopListTimeline(
            stops = stops.map { stop ->
                StopTimelineDisplay(
                    title = stop.name,
                    subtitle = null,
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            rowModifierFactory = { index -> Modifier.clickable { onToggle(index) } },
            trailingContent = { index ->
                Checkbox(
                    checked = stops[index].selected,
                    onCheckedChange = { onToggle(index) },
                )
            },
        )
    }
}
