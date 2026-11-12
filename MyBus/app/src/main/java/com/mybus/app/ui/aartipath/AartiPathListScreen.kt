package com.mybus.app.ui.aartipath

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mybus.app.R
import com.mybus.app.data.remote.dto.AartiPathItem
import com.mybus.app.data.repository.AartiPathRepository
import com.mybus.app.data.repository.formatPdfSize
import kotlin.math.roundToInt

const val AARTI_PATH_ROUTE = "aarti_path"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AartiPathListScreen(
    canManage: Boolean,
    onBack: () -> Unit,
    onOpen: (AartiPathItem) -> Unit,
    viewModel: AartiPathListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onPdfPicked(uri)
    }
    val choosePdf = { pickPdf.launch(arrayOf("application/pdf")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aarti & Path", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (canManage && state.error == null && !state.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.openAddSheet()
                        choosePdf()
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add PDF") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SearchField(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            state.offlineNotice?.let { OfflineNotice(it) }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    state.error != null -> CenteredMessage {
                        Text(
                            state.error.orEmpty(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::retry) { Text("Retry") }
                    }

                    state.items.isEmpty() -> CenteredMessage {
                        Icon(
                            ImageVector.vectorResource(R.drawable.ic_aarti_diya),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No aartis or paths yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (canManage) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap Add PDF to add the first one",
                                style = MaterialTheme.typography.bodyMedium,
                                color = mutedTextColor()
                            )
                        }
                    }

                    state.visibleItems.isEmpty() -> CenteredMessage {
                        Text(
                            "Nothing matches “${state.query.trim()}”",
                            style = MaterialTheme.typography.bodyLarge,
                            color = mutedTextColor(),
                            textAlign = TextAlign.Center
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (canManage) 96.dp else 16.dp)
                    ) {
                        items(state.visibleItems, key = { it.id }) { item ->
                            AartiPathRow(
                                item = item,
                                saved = item.id in savedIds,
                                canManage = canManage,
                                onOpen = { onOpen(item) },
                                onDelete = { viewModel.askDelete(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    state.addSheet?.let { sheet ->
        AddPdfSheet(
            state = sheet,
            onDismiss = viewModel::closeAddSheet,
            onChooseFile = choosePdf,
            onTitleChange = viewModel::setTitle,
            onUpload = viewModel::upload
        )
    }

    state.pendingDelete?.let { item ->
        DeleteDialog(
            item = item,
            isDeleting = state.isDeleting,
            onConfirm = viewModel::confirmDelete,
            onCancel = viewModel::cancelDelete
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val container = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search aarti or path") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun OfflineNotice(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = mutedTextColor()
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = mutedTextColor())
    }
}

/** Scrollable so pull-to-refresh also works on empty and error states. */
@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) { content() }
}

@Composable
private fun AartiPathRow(
    item: AartiPathItem,
    saved: Boolean,
    canManage: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, end = if (canManage) 4.dp else 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                MetaLine(item, saved)
            }
            if (canManage) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete ${item.title}",
                        tint = mutedTextColor()
                    )
                }
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = mutedTextColor()
                )
            }
        }
        HorizontalDivider(
            Modifier.padding(start = 70.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    }
}

@Composable
private fun MetaLine(item: AartiPathItem, saved: Boolean) {
    val pages = item.pageCount?.let { if (it == 1) "1 page" else "$it pages" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val muted = mutedTextColor()
        if (saved) {
            if (pages != null) {
                Text("$pages · ", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            val green = savedGreen()
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = green
            )
            Spacer(Modifier.width(3.dp))
            Text("Saved offline", style = MaterialTheme.typography.bodySmall, color = green)
        } else {
            val size = item.sizeBytes?.takeIf { it > 0 }?.let(::formatPdfSize)
            val text = listOfNotNull(pages, size).joinToString(" · ").ifEmpty { "PDF" }
            Text(text, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPdfSheet(
    state: AddPdfState,
    onDismiss: () -> Unit,
    onChooseFile: () -> Unit,
    onTitleChange: (String) -> Unit,
    onUpload: () -> Unit
) {
    val uploading by rememberUpdatedState(state.isUploading)
    // A swipe or a tap outside doesn't close the sheet while a PDF is uploading.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || !uploading }
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Add Aarti or Path",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            val picked = state.picked
            when {
                state.isInspecting -> Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Checking the PDF…", style = MaterialTheme.typography.bodyMedium)
                }

                picked != null -> Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(width = 34.dp, height = 40.dp)
                        ) {
                            Box(contentAlignment = Alignment.BottomCenter) {
                                Text(
                                    "PDF",
                                    color = MaterialTheme.colorScheme.onError,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 5.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                picked.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val pages = if (picked.pageCount == 1) "1 page" else "${picked.pageCount} pages"
                            Text(
                                "$pages · ${formatPdfSize(picked.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = mutedTextColor()
                            )
                        }
                        TextButton(onClick = onChooseFile, enabled = !state.isUploading) { Text("Change") }
                    }
                }

                else -> OutlinedButton(
                    onClick = onChooseFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose PDF")
                }
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                singleLine = true,
                enabled = !state.isUploading,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                supportingText = {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Shown in the list", modifier = Modifier.weight(1f))
                        Text("${state.title.length} / ${AartiPathRepository.MAX_TITLE_LENGTH}")
                    }
                }
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = onUpload,
                enabled = state.canUpload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    if (state.isUploading) "Uploading… ${(state.progress * 100).roundToInt()}%" else "Upload"
                )
            }
            if (state.isUploading) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "PDF only, up to 20 MB",
                style = MaterialTheme.typography.bodySmall,
                color = mutedTextColor(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DeleteDialog(
    item: AartiPathItem,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        iconContentColor = MaterialTheme.colorScheme.error,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Delete ${item.title}?", textAlign = TextAlign.Center) },
        text = { Text("It will be removed from the list for everyone. This can't be undone.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isDeleting) { Text("Cancel") }
        }
    )
}

@Composable
internal fun mutedTextColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)

@Composable
private fun savedGreen(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF81C784) else Color(0xFF2E7D32)
