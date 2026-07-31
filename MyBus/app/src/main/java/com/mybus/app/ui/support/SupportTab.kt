package com.mybus.app.ui.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mybus.app.data.remote.dto.FeedbackItem
import com.mybus.app.data.remote.dto.HelpContact
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportTab(
    isLoggedIn: Boolean,
    isAdmin: Boolean,
    onRequireLogin: () -> Unit,
    viewModel: SupportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedTab, isAdmin) {
        if (selectedTab == 1 && isAdmin && uiState.feedback.isEmpty()) {
            viewModel.loadFeedback()
        } else if (selectedTab != 1) {
            viewModel.clearSubmissionConfirmation()
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.clearSubmissionConfirmation() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Support") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                listOf("Help", "Feedback").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    HelpContent(
                        uiState = uiState,
                        onRetry = viewModel::loadHelpContacts
                    )
                } else if (isAdmin) {
                    AdminFeedbackContent(
                        uiState = uiState,
                        onRetry = { viewModel.loadFeedback() },
                        onLoadMore = { viewModel.loadFeedback(reset = false) }
                    )
                } else {
                    UserFeedbackContent(
                        uiState = uiState,
                        isLoggedIn = isLoggedIn,
                        onRequireLogin = onRequireLogin,
                        onMessageChange = viewModel::updateMessage,
                        onSubmit = viewModel::submitFeedback
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpContent(uiState: SupportUiState, onRetry: () -> Unit) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "How can we help?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Choose a service below to call the support contact.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (uiState.helpContacts.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column {
                        uiState.helpContacts.forEachIndexed { index, contact ->
                            HelpContactRow(
                                contact = contact,
                                onCall = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                                    )
                                }
                            )
                            if (index != uiState.helpContacts.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
        if (uiState.isLoadingHelp) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        uiState.helpError?.let { message ->
            item {
                Column {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpContactRow(contact: HelpContact, onCall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCall)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = helpIcon(contact.code),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = contact.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = contact.contactName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = contact.phone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Icon(Icons.Filled.Phone, contentDescription = "Call ${contact.title}")
    }
}

private fun helpIcon(code: String): ImageVector = when (code) {
    "bus" -> Icons.Filled.DirectionsBus
    "pooja" -> Icons.Filled.ConfirmationNumber
    "dharamshala" -> Icons.Filled.Hotel
    "emergency" -> Icons.Filled.LocalHospital
    else -> Icons.Filled.Phone
}

@Composable
private fun UserFeedbackContent(
    uiState: SupportUiState,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onMessageChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Share your feedback",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tell us what we can improve. Admin may contact you using your account details.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isLoggedIn) {
                        OutlinedTextField(
                            value = uiState.message,
                            onValueChange = onMessageChange,
                            label = { Text("Your feedback") },
                            supportingText = { Text("${uiState.message.length}/2000") },
                            minLines = 5,
                            maxLines = 10,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isSubmitting
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onSubmit,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isSubmitting && uiState.message.isNotBlank()
                        ) {
                            if (uiState.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Send feedback")
                            }
                        }
                    } else {
                        Text("Please sign in so your feedback can include your contact details.")
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onRequireLogin, modifier = Modifier.fillMaxWidth()) {
                            Text("Sign in to continue")
                        }
                    }
                }
            }
        }
        if (uiState.submissionComplete) {
            item {
                Text(
                    text = "Thank you. Your feedback has been sent to Admin.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        uiState.submissionError?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AdminFeedbackContent(
    uiState: SupportUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "User feedback",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Contact the user directly by phone or email if a follow-up is needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (uiState.feedback.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        uiState.feedback.forEachIndexed { index, feedback ->
                            FeedbackRow(feedback)
                            if (index != uiState.feedback.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        } else if (!uiState.isLoadingFeedback && uiState.feedbackError == null) {
            item { Text("No feedback has been submitted yet.") }
        }

        uiState.feedbackError?.let { message ->
            item {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Try again")
                    }
                }
            }
        }

        if (uiState.isLoadingFeedback) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (uiState.feedback.size < uiState.total) {
            item {
                OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("Load more")
                }
            }
        }
    }
}

@Composable
private fun FeedbackRow(feedback: FeedbackItem) {
    val context = LocalContext.current
    val submitter = feedback.submittedBy
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(feedback.message, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = formatFeedbackTime(feedback.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = submitter.name?.takeIf(String::isNotBlank) ?: "User",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp)
        )
        submitter.phone?.takeIf(String::isNotBlank)?.let { phone ->
            ContactLine(
                text = phone,
                icon = Icons.Filled.Phone,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                }
            )
        }
        submitter.email?.takeIf(String::isNotBlank)?.let { email ->
            ContactLine(
                text = email,
                icon = Icons.Filled.Email,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
                }
            )
        }
    }
}

@Composable
private fun ContactLine(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun formatFeedbackTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("dd MMM yy, h:mm a")
        .format(Instant.parse(value).atZone(ZoneId.systemDefault()))
}.getOrDefault(value)
