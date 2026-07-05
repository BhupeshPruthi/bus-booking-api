package com.mybus.app.ui.pooja

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybus.app.data.remote.dto.PoojaTokenHistoryItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPoojaTokensScreen(
    onBack: () -> Unit,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    viewModel: MyPoojaTokensViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadTokens()
    }

    LaunchedEffect(state.requiresLogin) {
        if (state.requiresLogin) onRequireLogin()
    }

    LaunchedEffect(isLoggedIn, state.requiresLogin) {
        if (isLoggedIn && state.requiresLogin) {
            viewModel.loadTokens()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Pooja Tokens", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadTokens() }) {
                        Text("Retry")
                    }
                }
            }

            state.requiresLogin -> {
                LoginRequiredView(
                    onLoginClick = onRequireLogin,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            state.tokens.isEmpty() -> {
                EmptyPoojaTokensView(modifier = Modifier.padding(innerPadding))
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.tokens, key = { it.id }) { token ->
                        PoojaTokenHistoryCard(token = token)
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LoginRequiredView(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Log in to view your pooja tokens",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLoginClick) {
            Text("Log in")
        }
    }
}

@Composable
private fun EmptyPoojaTokensView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.SelfImprovement,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "You have never booked any pooja.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PoojaTokenHistoryCard(token: PoojaTokenHistoryItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = token.place,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                PoojaTokenStatusChip(status = token.status)
            }

            IconTextRow(
                icon = Icons.Filled.CalendarToday,
                text = formatDateTime(token.scheduledAt)
            )
            IconTextRow(
                icon = Icons.Filled.ConfirmationNumber,
                text = token.tokenNumber?.let { "Token #$it" } ?: "Token number pending"
            )
            IconTextRow(
                icon = Icons.Filled.LocationOn,
                text = "${token.memberCount} member(s) - ${token.city}"
            )
            token.createdAt?.let {
                Text(
                    text = "Booked: ${formatDateTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun IconTextRow(
    icon: ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PoojaTokenStatusChip(status: String) {
    val color = when (status) {
        "confirmed" -> MaterialTheme.colorScheme.primary
        "cancelled" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = status.replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
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
