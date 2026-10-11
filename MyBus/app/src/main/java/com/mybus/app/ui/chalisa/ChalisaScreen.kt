package com.mybus.app.ui.chalisa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mybus.app.data.remote.dto.ChalisaBoardEntry
import kotlinx.coroutines.delay
import java.time.Instant

const val CHALISA_TAB_GROUP = 0
const val CHALISA_TAB_MY_PATH = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChalisaScreen(
    initialTab: Int = CHALISA_TAB_GROUP,
    onBack: () -> Unit,
    onOpenArchive: () -> Unit,
    onSchedule: (marathonId: String?) -> Unit,
    viewModel: ChalisaViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    // Board refresh every 30 seconds, only while the screen is visible.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay((state.board?.refreshAfterSeconds ?: 30).coerceAtLeast(15) * 1000L)
                viewModel.refreshBoard()
            }
        }
    }

    // Clock for "starts in" and for switching to +1 at the start time.
    var now by remember { mutableStateOf(state.now()) }
    LaunchedEffect(state.serverOffsetMillis) {
        while (true) {
            now = state.now()
            delay(15_000)
        }
    }
    val phase = state.phase(now)
    LaunchedEffect(state.marathon?.state, phase) {
        if (state.marathon?.state == "scheduled" && phase == MarathonPhase.RUNNING) viewModel.refresh()
    }

    // The lock-screen counter is on by default: once the reader has joined a running marathon it
    // appears on the lock screen. Android 13+ asks for notification permission first; refusing
    // turns it off.
    val context = LocalContext.current
    val lockScreenPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.showLockScreenCounterIfOn() else viewModel.onNotificationPermissionDenied()
    }
    val countingOpen = phase == MarathonPhase.RUNNING && state.isJoined
    LaunchedEffect(countingOpen, state.lockScreenOn) {
        if (!countingOpen || !state.lockScreenOn) return@LaunchedEffect
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            lockScreenPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.showLockScreenCounterIfOn()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = if (message.action != null) "Undo" else null,
                withDismissAction = message.action != null,
                duration = if (message.action != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                when (val action = message.action) {
                    is ChalisaAction.UndoRound -> viewModel.undo(action.clientRequestId)
                    is ChalisaAction.RestoreMarathon -> viewModel.restoreMarathon(action.marathonId)
                    null -> Unit
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hanuman Chalisa", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.canManage) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Marathon options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                val marathonId = state.marathon?.id
                                when (phase) {
                                    MarathonPhase.NONE -> DropdownMenuItem(
                                        text = { Text("Schedule a marathon") },
                                        onClick = { menuOpen = false; onSchedule(null) }
                                    )
                                    MarathonPhase.SCHEDULED -> {
                                        DropdownMenuItem(
                                            text = { Text("Edit schedule") },
                                            onClick = { menuOpen = false; onSchedule(marathonId) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Cancel marathon", color = MaterialTheme.colorScheme.error) },
                                            onClick = { menuOpen = false; viewModel.showCancelDialog(true) }
                                        )
                                    }
                                    MarathonPhase.RUNNING -> {
                                        DropdownMenuItem(
                                            text = { Text("Edit name and goal") },
                                            onClick = { menuOpen = false; onSchedule(marathonId) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("End marathon", color = MaterialTheme.colorScheme.error) },
                                            onClick = { menuOpen = false; viewModel.showEndSheet(true) }
                                        )
                                    }
                                }
                                DropdownMenuItem(
                                    text = { Text("Past marathons") },
                                    onClick = { menuOpen = false; onOpenArchive() }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                val unselected = MaterialTheme.colorScheme.onSurfaceVariant
                Tab(
                    selected = selectedTab == CHALISA_TAB_GROUP,
                    onClick = { selectedTab = CHALISA_TAB_GROUP },
                    text = { Text("Group marathon") },
                    unselectedContentColor = unselected
                )
                Tab(
                    selected = selectedTab == CHALISA_TAB_MY_PATH,
                    onClick = { selectedTab = CHALISA_TAB_MY_PATH },
                    text = { Text("My path") },
                    unselectedContentColor = unselected
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh(showSpinner = true) },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.isLoading && state.board == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.loadError != null && state.board == null -> LoadErrorContent(state.loadError!!) {
                        viewModel.refresh(showSpinner = true)
                    }
                    selectedTab == CHALISA_TAB_GROUP -> GroupMarathonTab(
                        state = state,
                        phase = phase,
                        now = now,
                        viewModel = viewModel,
                        onOpenArchive = onOpenArchive,
                        onSchedule = { onSchedule(null) }
                    )
                    else -> MyPathTab(state = state, phase = phase, viewModel = viewModel)
                }
            }
        }
    }

    state.pendingStartingCount?.let { count ->
        StartingCountConfirmDialog(
            count = count,
            marathonTitle = state.marathon?.title,
            onChange = viewModel::dismissStartingCount,
            onConfirm = viewModel::confirmStartingCount
        )
    }

    if (state.showEndSheet) {
        EndMarathonSheet(state = state, viewModel = viewModel)
    }

    if (state.showCancelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showCancelDialog(false) },
            title = { Text("Cancel the scheduled marathon?") },
            text = { Text("It will disappear from everyone's Home page. Nobody has read in it yet, so nothing is lost.") },
            confirmButton = {
                TextButton(onClick = viewModel::cancelScheduledMarathon) {
                    Text("Cancel marathon", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.showCancelDialog(false) }) { Text("Keep it") } }
        )
    }
}

@Composable
private fun LoadErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

// ---------------- Group marathon tab ----------------

@Composable
private fun GroupMarathonTab(
    state: ChalisaUiState,
    phase: MarathonPhase,
    now: Instant,
    viewModel: ChalisaViewModel,
    onOpenArchive: () -> Unit,
    onSchedule: () -> Unit
) {
    val board = state.board
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (phase == MarathonPhase.NONE || board?.marathon == null) {
            item { NoMarathonCard(state = state, now = now, onSchedule = onSchedule, onRestore = viewModel::restoreMarathon) }
        } else {
            item { MarathonHeaderCard(state = state, phase = phase, now = now) }
            if (!state.isJoined) {
                item { JoinCard(state = state, phase = phase, viewModel = viewModel) }
            } else {
                item { MyRankRow(rank = board.me?.rank, count = board.me?.count ?: 0) }
            }
            if (board.leaderboard.isEmpty()) {
                item {
                    Text(
                        if (phase == MarathonPhase.RUNNING) "Nobody has counted yet. Be the first."
                        else "The board fills up once the marathon starts.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                items(board.leaderboard, key = { it.participantId }) { entry ->
                    BoardRow(entry = entry, isMe = entry.participantId == state.participant?.id)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = onOpenArchive) { Text("Past marathons") }
                if (phase != MarathonPhase.NONE) {
                    Text(
                        "Updates every 30 seconds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SaffronCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
private fun MarathonHeaderCard(state: ChalisaUiState, phase: MarathonPhase, now: Instant) {
    val board = state.board ?: return
    val marathon = board.marathon ?: return
    val start = parseInstant(marathon.startsAt)
    SaffronCard {
        Text(marathon.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (phase == MarathonPhase.SCHEDULED && start != null) {
            Text("Starts ${formatStartsIn(start, now)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(formatStart(start), style = MaterialTheme.typography.bodyMedium)
            marathon.goal?.let { Text("Goal ${formatCount(it)} readings", style = MaterialTheme.typography.bodySmall) }
        } else {
            start?.let { Text("Since ${formatShortDate(it)}", style = MaterialTheme.typography.bodySmall) }
            Text(formatCount(board.total), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            val goal = marathon.goal
            if (goal != null && goal > 0) {
                val progress = (board.total.toFloat() / goal).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp)
                )
                Text(
                    "${(progress * 100).toInt()}% of ${formatCount(goal)} goal · ${formatCount(board.participantCount)} devotees",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text("readings by ${formatCount(board.participantCount)} devotees", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NoMarathonCard(
    state: ChalisaUiState,
    now: Instant,
    onSchedule: () -> Unit,
    onRestore: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text("No marathon is running right now", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            "When the next marathon is scheduled, it'll appear on the Home page. Your own readings are always in your Profile.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (state.canManage) {
            val ended = state.recentlyEnded
            val restoreUntil = parseInstant(ended?.restoreUntil)
            if (ended != null && restoreUntil != null && restoreUntil.isAfter(now)) {
                OutlinedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "You ended ${ended.title}. You can undo this until ${formatTime(restoreUntil)}.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onRestore(ended.id) }) { Text("Undo") }
                    }
                }
            }
            Button(onClick = onSchedule, modifier = Modifier.padding(top = 8.dp)) { Text("Schedule a marathon") }
        }
    }
}

@Composable
private fun MyRankRow(rank: Int?, count: Long) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (rank != null) "You · rank $rank" else "You haven't counted in this marathon yet",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            if (rank != null) Text(formatCount(count), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BoardRow(entry: ChalisaBoardEntry, isMe: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            entry.rank.toString(),
            modifier = Modifier.width(36.dp),
            fontWeight = if (entry.rank <= 3) FontWeight.Bold else FontWeight.Normal,
            color = if (entry.rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            entry.displayName + if (isMe) " (you)" else "",
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isMe) FontWeight.SemiBold else FontWeight.Normal
        )
        if (entry.isGuest) {
            Text("guest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        Text(formatCount(entry.count), fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

// ---------------- Join ----------------

@Composable
private fun JoinCard(state: ChalisaUiState, phase: MarathonPhase, viewModel: ChalisaViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Join the group path", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (phase == MarathonPhase.RUNNING) "Enter the name to show on the board, then tap +1 after each round."
                else "Enter your name now. Counting opens when the marathon starts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.joinName,
                onValueChange = viewModel::onJoinNameChange,
                label = { Text("Your name on the board") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            if (phase == MarathonPhase.RUNNING) {
                StartingCountField(state = state, viewModel = viewModel)
            }
            Button(
                onClick = viewModel::join,
                enabled = !state.isJoining,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (state.isJoining) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (phase == MarathonPhase.RUNNING) "Start counting" else "Join")
            }
        }
    }
}

@Composable
private fun StartingCountField(state: ChalisaUiState, viewModel: ChalisaViewModel) {
    OutlinedTextField(
        value = state.startingCountText,
        onValueChange = viewModel::onStartingCountChange,
        label = { Text("Already read? Starting count") },
        supportingText = {
            Text("Leave it at 0 if you're starting fresh. You can add it once, before your first round.")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StartingCountConfirmDialog(
    count: Int,
    marathonTitle: String?,
    onChange: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onChange,
        title = { Text("Add your starting count?") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(formatCount(count), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("readings already done", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    "These will be added to ${marathonTitle ?: "the marathon"} and your lifetime count. " +
                        "You can add a starting count only once for this marathon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Yes, add ${formatCount(count)}") } },
        dismissButton = { TextButton(onClick = onChange) { Text("Change") } }
    )
}

// ---------------- My path tab ----------------

@Composable
private fun MyPathTab(state: ChalisaUiState, phase: MarathonPhase, viewModel: ChalisaViewModel) {
    val me = state.me
    val haptics = LocalHapticFeedback.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!state.isJoined) {
            if (phase != MarathonPhase.NONE) {
                item { JoinCard(state = state, phase = phase, viewModel = viewModel) }
            } else {
                item {
                    Text(
                        "Counting opens when the next marathon starts.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        border = BorderStroke(8.dp, MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.size(140.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatCount(me?.today ?: 0), fontSize = 38.sp, fontWeight = FontWeight.Bold)
                            Text("today", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val marathonCount = state.board?.me?.count ?: 0
                    Text(
                        buildString {
                            append("Lifetime ${formatCount(me?.lifetime ?: 0)}")
                            if (phase != MarathonPhase.NONE) append(" · Marathon ${formatCount(marathonCount)}")
                            if ((me?.streakDays ?: 0) > 0) append(" · ${me?.streakDays}-day streak")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                when (phase) {
                    MarathonPhase.RUNNING -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.countRound()
                            },
                            enabled = !state.isCounting,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        ) {
                            Text("+1 Round complete", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                        val lastRead = parseInstant(me?.lastReadAt)
                        val tap = state.lastTap
                        val canUndo = tap != null && parseInstant(tap.undoUntil)?.isAfter(Instant.now()) == true
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (lastRead != null) {
                                Text(
                                    (if (tap?.kind == "opening") "Starting count added at " else "Last round at ") +
                                        formatLastRound(lastRead),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canUndo) TextButton(onClick = { viewModel.undo(tap!!.clientRequestId) }) { Text("Undo") }
                        }
                    }
                    MarathonPhase.SCHEDULED -> {
                        val start = parseInstant(state.marathon?.startsAt)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (start != null) "Counting opens ${formatStart(start)}" else "Counting opens soon",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    MarathonPhase.NONE -> Text(
                        "Counting opens when the next marathon starts.",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (phase == MarathonPhase.RUNNING) {
                item { LockScreenToggle(state = state, viewModel = viewModel) }
            }

            if (phase == MarathonPhase.RUNNING && me?.canSetStartingCount == true) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Already read before opening the app?", fontWeight = FontWeight.SemiBold)
                            StartingCountField(state = state, viewModel = viewModel)
                            OutlinedButton(onClick = viewModel::addStartingCount, enabled = !state.isJoining) {
                                Text("Add starting count")
                            }
                        }
                    }
                }
            }
        }

        if (me != null && (state.isJoined || me.lifetime > 0)) {
            item { HistoryHeader(state = state, viewModel = viewModel) }
            if (me.days.isEmpty()) {
                item {
                    Text(
                        "No readings in ${formatMonth(state.historyMonth)}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(me.days, key = { it.date }) { day ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(formatHistoryDay(day.date), modifier = Modifier.weight(1f))
                        Text(formatCount(day.count), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LockScreenToggle(state: ChalisaUiState, viewModel: ChalisaViewModel) {
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setLockScreenCounter(true) else viewModel.onNotificationPermissionDenied()
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Count from lock screen", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Saves battery: tap +1 without unlocking your phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.lockScreenOn,
                    onCheckedChange = { turnOn ->
                        if (turnOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setLockScreenCounter(turnOn)
                        }
                    }
                )
            }
            if (state.lockScreenOn) {
                Text(
                    "If you don't see it on the lock screen, allow notification content there in your phone's lock screen settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryHeader(state: ChalisaUiState, viewModel: ChalisaViewModel) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Your history · ${formatMonth(state.historyMonth)}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { viewModel.showMonth(state.historyMonth.minusMonths(1)) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        val isCurrentMonth = state.historyMonth == java.time.YearMonth.now(ChalisaZone)
        IconButton(onClick = { viewModel.showMonth(state.historyMonth.plusMonths(1)) }, enabled = !isCurrentMonth) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
}

// ---------------- Super admin: end ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndMarathonSheet(state: ChalisaUiState, viewModel: ChalisaViewModel) {
    val marathon = state.marathon ?: return
    val board = state.board
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val typedCorrectly = state.endConfirmText.trim() == "END"
    var triedWithoutConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = { viewModel.showEndSheet(false) }, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("End the marathon for everyone?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(marathon.title, fontWeight = FontWeight.SemiBold)
                    val start = parseInstant(marathon.startsAt)
                    Text(
                        "${formatCount(board?.total ?: 0)} readings · ${formatCount(board?.participantCount ?: 0)} devotees" +
                            (start?.let { " · since ${formatShortDate(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "Counting stops for all ${formatCount(board?.participantCount ?: 0)} devotees",
                    "It disappears from everyone's Home page",
                    "Results are saved in Past marathons",
                    "Everyone's lifetime count stays the same"
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            OutlinedTextField(
                value = state.endConfirmText,
                onValueChange = { triedWithoutConfirm = false; viewModel.onEndConfirmChange(it) },
                label = { Text("Type END to confirm") },
                singleLine = true,
                isError = triedWithoutConfirm && !typedCorrectly,
                supportingText = if (triedWithoutConfirm && !typedCorrectly) {
                    { Text("Type END exactly to continue") }
                } else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.showEndSheet(false) }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { if (typedCorrectly) viewModel.endMarathon() else triedWithoutConfirm = true },
                    enabled = !state.isEnding,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (state.isEnding) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("End marathon")
                }
            }
        }
    }
}
