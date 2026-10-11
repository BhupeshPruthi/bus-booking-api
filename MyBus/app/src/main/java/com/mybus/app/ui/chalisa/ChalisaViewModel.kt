package com.mybus.app.ui.chalisa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.local.ChalisaStore
import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.dto.ChalisaArchivedMarathon
import com.mybus.app.data.remote.dto.ChalisaBoard
import com.mybus.app.data.remote.dto.ChalisaMyStats
import com.mybus.app.data.remote.dto.ChalisaReading
import com.mybus.app.data.repository.ChalisaErrorCodes
import com.mybus.app.data.repository.ChalisaException
import com.mybus.app.data.repository.ChalisaRepository
import com.mybus.app.ui.chalisa.lockscreen.ChalisaLockScreenCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import javax.inject.Inject

/** Effective state of the marathon, with "scheduled" turning into "running" at the start time. */
enum class MarathonPhase { NONE, SCHEDULED, RUNNING }

data class ChalisaUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadError: String? = null,
    val board: ChalisaBoard? = null,
    val me: ChalisaMyStats? = null,
    val historyMonth: YearMonth = YearMonth.now(ChalisaZone),
    val joinName: String = "",
    val startingCountText: String = "0",
    val isJoining: Boolean = false,
    /** Non-null while the starting-count confirmation is shown. */
    val pendingStartingCount: Int? = null,
    val isCounting: Boolean = false,
    val lastTap: ChalisaReading? = null,
    val canManage: Boolean = false,
    val serverOffsetMillis: Long = 0,
    val showEndSheet: Boolean = false,
    val endConfirmText: String = "",
    val isEnding: Boolean = false,
    val showCancelDialog: Boolean = false,
    val lockScreenOn: Boolean = true,
    /** The marathon this super admin just ended, while undo is still possible. */
    val recentlyEnded: ChalisaArchivedMarathon? = null
) {
    val participant get() = me?.participant
    val isJoined get() = participant != null
    val marathon get() = board?.marathon

    fun now(): Instant = Instant.now().plusMillis(serverOffsetMillis)

    fun phase(now: Instant = now()): MarathonPhase {
        val m = marathon ?: return MarathonPhase.NONE
        return when (m.state) {
            "running" -> MarathonPhase.RUNNING
            "scheduled" -> {
                val start = parseInstant(m.startsAt)
                if (start != null && !start.isAfter(now)) MarathonPhase.RUNNING else MarathonPhase.SCHEDULED
            }
            else -> MarathonPhase.NONE
        }
    }
}

sealed interface ChalisaAction {
    data class UndoRound(val clientRequestId: String) : ChalisaAction
    data class RestoreMarathon(val marathonId: String) : ChalisaAction
}

data class ChalisaMessage(val text: String, val action: ChalisaAction? = null)

@HiltViewModel
class ChalisaViewModel @Inject constructor(
    private val repository: ChalisaRepository,
    private val tokenManager: TokenManager,
    private val chalisaStore: ChalisaStore,
    private val lockScreenCounter: ChalisaLockScreenCounter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChalisaUiState())
    val uiState: StateFlow<ChalisaUiState> = _uiState.asStateFlow()

    private val _messages = Channel<ChalisaMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            tokenManager.canManageChalisa.collect { canManage -> _uiState.update { it.copy(canManage = canManage) } }
        }
        viewModelScope.launch {
            chalisaStore.lockScreenCounter.collect { on -> _uiState.update { it.copy(lockScreenOn = on) } }
        }
        viewModelScope.launch {
            val profileName = tokenManager.userName.firstOrNull().orEmpty()
            _uiState.update { if (it.joinName.isBlank()) it.copy(joinName = profileName) else it }
        }
        refresh()
    }

    private fun send(text: String, action: ChalisaAction? = null) {
        _messages.trySend(ChalisaMessage(text, action))
    }

    private fun offsetFrom(serverTime: String?): Long? =
        parseInstant(serverTime)?.let { it.toEpochMilli() - System.currentTimeMillis() }

    private var hasResumed = false

    /**
     * The screen came back into view, e.g. after scheduling or editing a marathon from it.
     * The first resume is skipped because init already loads.
     */
    fun onResume() {
        if (hasResumed) refresh() else hasResumed = true
    }

    /** Loads the board and personal stats together. */
    fun refresh(showSpinner: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = showSpinner) }
            val board = async { repository.board() }
            val me = async { repository.myStats(_uiState.value.historyMonth.toString()) }
            val boardResult = board.await()
            val meResult = me.await()
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isRefreshing = false,
                    board = boardResult.getOrNull() ?: state.board,
                    me = meResult.getOrNull() ?: state.me,
                    serverOffsetMillis = offsetFrom(boardResult.getOrNull()?.serverTime) ?: state.serverOffsetMillis,
                    loadError = if (boardResult.isFailure && state.board == null) {
                        boardResult.exceptionOrNull()?.message
                    } else null
                )
            }
        }
    }

    /** Quiet refresh of the board while the screen is open. */
    fun refreshBoard() {
        viewModelScope.launch {
            repository.board().onSuccess { board ->
                _uiState.update {
                    it.copy(board = board, serverOffsetMillis = offsetFrom(board.serverTime) ?: it.serverOffsetMillis)
                }
            }
        }
    }

    fun onJoinNameChange(value: String) = _uiState.update { it.copy(joinName = value.take(40)) }

    fun onStartingCountChange(value: String) =
        _uiState.update { it.copy(startingCountText = value.filter(Char::isDigit).take(4)) }

    private fun startingCountOrNull(): Int? {
        val state = _uiState.value
        val count = state.startingCountText.toIntOrNull() ?: 0
        val max = state.me?.maxStartingCount ?: 5000
        if (count > max) {
            send("The starting count can be at most ${formatCount(max)}.")
            return null
        }
        return count
    }

    /** Join button: a starting count above 0 is confirmed first. */
    fun join() {
        val state = _uiState.value
        val name = state.joinName.trim()
        if (name.length < 2) {
            send("Enter your name, at least 2 letters.")
            return
        }
        val count = if (state.phase() == MarathonPhase.RUNNING) startingCountOrNull() ?: return else 0
        if (count > 0) {
            _uiState.update { it.copy(pendingStartingCount = count) }
        } else {
            saveJoin(name, 0)
        }
    }

    /** "Add starting count" for someone who has joined before but not read in this marathon. */
    fun addStartingCount() {
        val count = startingCountOrNull() ?: return
        if (count == 0) {
            send("Enter how many times you've already read.")
            return
        }
        _uiState.update { it.copy(pendingStartingCount = count) }
    }

    fun dismissStartingCount() = _uiState.update { it.copy(pendingStartingCount = null) }

    fun confirmStartingCount() {
        val count = _uiState.value.pendingStartingCount ?: return
        _uiState.update { it.copy(pendingStartingCount = null) }
        if (_uiState.value.isJoined) {
            viewModelScope.launch {
                _uiState.update { it.copy(isJoining = true) }
                repository.addStartingCount(count)
                    .onSuccess { result ->
                        _uiState.update { it.copy(startingCountText = "0", lastTap = result.reading) }
                        lockScreenCounter.onCountsChanged(result)
                        result.reading?.let {
                            send("Added ${formatCount(count)} readings.", ChalisaAction.UndoRound(it.clientRequestId))
                        }
                    }
                    .onFailure { handleFailure(it) }
                _uiState.update { it.copy(isJoining = false) }
                refresh()
            }
        } else {
            saveJoin(_uiState.value.joinName.trim(), count)
        }
    }

    private fun saveJoin(name: String, startingCount: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isJoining = true) }
            repository.join(name)
                .onSuccess {
                    if (startingCount > 0) {
                        repository.addStartingCount(startingCount)
                            .onSuccess { result ->
                                _uiState.update { it.copy(startingCountText = "0", lastTap = result.reading) }
                                lockScreenCounter.onCountsChanged(result)
                                result.reading?.let {
                                    send("Added ${formatCount(startingCount)} readings.", ChalisaAction.UndoRound(it.clientRequestId))
                                }
                            }
                            .onFailure { handleFailure(it) }
                    }
                    if (startingCount == 0) send("You're in as $name.")
                }
                .onFailure { handleFailure(it) }
            _uiState.update { it.copy(isJoining = false) }
            refresh()
        }
    }

    /** "+1 Round complete" */
    fun countRound() {
        if (_uiState.value.isCounting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCounting = true) }
            repository.addRound()
                .onSuccess { result ->
                    _uiState.update { state ->
                        state.copy(
                            lastTap = result.reading,
                            me = state.me?.copy(
                                today = result.today,
                                lifetime = result.lifetime,
                                lastReadAt = result.lastReadAt,
                                canSetStartingCount = result.canSetStartingCount
                            ),
                            board = state.board?.let { board ->
                                board.copy(
                                    total = result.marathon?.total ?: board.total,
                                    participantCount = result.marathon?.participantCount ?: board.participantCount,
                                    me = board.me?.copy(count = result.marathon?.myCount ?: board.me.count)
                                )
                            }
                        )
                    }
                    result.reading?.let { send("Round counted.", ChalisaAction.UndoRound(it.clientRequestId)) }
                    lockScreenCounter.onCountsChanged(result)
                    refreshBoard()
                    // Keeps the day-by-day history in step with the new round.
                    repository.myStats(_uiState.value.historyMonth.toString())
                        .onSuccess { me -> _uiState.update { it.copy(me = me) } }
                }
                .onFailure { handleFailure(it) }
            _uiState.update { it.copy(isCounting = false) }
        }
    }

    fun undo(clientRequestId: String) {
        viewModelScope.launch {
            repository.undo(clientRequestId)
                .onSuccess { result ->
                    _uiState.update { it.copy(lastTap = null) }
                    chalisaStore.clearLastTap()
                    lockScreenCounter.onCountsChanged(result)
                    send("Removed.")
                }
                .onFailure { handleFailure(it) }
            refresh()
        }
    }

    /** "Count from lock screen" switch. Permission is asked for by the screen first. */
    fun setLockScreenCounter(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                lockScreenCounter.start()
                send("The counter is on your lock screen. Tap +1 there after each round.")
            } else {
                lockScreenCounter.stop()
            }
        }
    }

    /** Shows the lock-screen counter if it's on (the default) and not already showing. */
    fun showLockScreenCounterIfOn() {
        viewModelScope.launch { lockScreenCounter.showIfOn() }
    }

    /** Without notifications there's no lock-screen counter, so the switch shows it as off. */
    fun onNotificationPermissionDenied() {
        viewModelScope.launch { lockScreenCounter.stop() }
        send("Allow notifications for Balaji Sevak to count from the lock screen.")
    }

    fun showMonth(month: YearMonth) {
        if (month.isAfter(YearMonth.now(ChalisaZone))) return
        _uiState.update { it.copy(historyMonth = month) }
        viewModelScope.launch {
            repository.myStats(month.toString()).onSuccess { me -> _uiState.update { it.copy(me = me) } }
        }
    }

    private fun handleFailure(error: Throwable) {
        val code = (error as? ChalisaException)?.code
        send(error.message ?: "Something went wrong. Please try again.")
        when (code) {
            ChalisaErrorCodes.MARATHON_NOT_RUNNING,
            ChalisaErrorCodes.MARATHON_NOT_STARTED,
            ChalisaErrorCodes.NAME_REQUIRED,
            ChalisaErrorCodes.INVALID_GUEST_TOKEN,
            ChalisaErrorCodes.STARTING_COUNT_NOT_ALLOWED -> refresh()
        }
    }

    // ---- Super admin ----

    fun showEndSheet(show: Boolean) = _uiState.update { it.copy(showEndSheet = show, endConfirmText = "") }

    fun onEndConfirmChange(value: String) = _uiState.update { it.copy(endConfirmText = value.take(10)) }

    fun endMarathon() {
        val state = _uiState.value
        val marathonId = state.marathon?.id ?: return
        if (state.endConfirmText.trim() != "END") return
        viewModelScope.launch {
            _uiState.update { it.copy(isEnding = true) }
            repository.end(marathonId, state.endConfirmText)
                .onSuccess { archived ->
                    _uiState.update { it.copy(showEndSheet = false, recentlyEnded = archived) }
                    send("Marathon ended and archived.", ChalisaAction.RestoreMarathon(archived.id))
                }
                .onFailure { handleFailure(it) }
            _uiState.update { it.copy(isEnding = false) }
            refresh()
        }
    }

    fun restoreMarathon(marathonId: String) {
        viewModelScope.launch {
            repository.restore(marathonId)
                .onSuccess {
                    _uiState.update { it.copy(recentlyEnded = null) }
                    send("The marathon is running again.")
                }
                .onFailure {
                    _uiState.update { state -> state.copy(recentlyEnded = null) }
                    handleFailure(it)
                }
            refresh()
        }
    }

    fun showCancelDialog(show: Boolean) = _uiState.update { it.copy(showCancelDialog = show) }

    fun cancelScheduledMarathon() {
        val marathonId = _uiState.value.marathon?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showCancelDialog = false) }
            repository.cancel(marathonId)
                .onSuccess { send("The scheduled marathon was cancelled.") }
                .onFailure { handleFailure(it) }
            refresh()
        }
    }
}
