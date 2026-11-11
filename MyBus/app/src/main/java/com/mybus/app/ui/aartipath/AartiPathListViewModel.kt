package com.mybus.app.ui.aartipath

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.remote.dto.AartiPathItem
import com.mybus.app.data.repository.AartiPathException
import com.mybus.app.data.repository.AartiPathRepository
import com.mybus.app.data.repository.PickedPdf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The admin's "Add Aarti or Path" sheet. */
data class AddPdfState(
    val picked: PickedPdf? = null,
    val title: String = "",
    val isInspecting: Boolean = false,
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null
) {
    val canUpload: Boolean
        get() = picked != null && title.isNotBlank() && !isUploading && !isInspecting
}

data class AartiPathListUiState(
    /** First load with nothing saved on the phone yet. */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val items: List<AartiPathItem> = emptyList(),
    /** Full-screen error, only when there is no list to show at all. */
    val error: String? = null,
    /** The list on screen is the saved copy because the refresh failed. */
    val showingSavedList: Boolean = false,
    val offlineNotice: String? = null,
    val query: String = "",
    val addSheet: AddPdfState? = null,
    val pendingDelete: AartiPathItem? = null,
    val isDeleting: Boolean = false
) {
    val visibleItems: List<AartiPathItem>
        get() {
            val q = query.trim()
            return if (q.isEmpty()) items else items.filter { it.title.contains(q, ignoreCase = true) }
        }
}

@HiltViewModel
class AartiPathListViewModel @Inject constructor(
    private val repository: AartiPathRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AartiPathListUiState())
    val uiState: StateFlow<AartiPathListUiState> = _uiState.asStateFlow()

    /** Ids of PDFs saved on the phone ("Saved offline"). */
    val savedIds: StateFlow<Set<String>> = repository.savedIds

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off snackbar messages. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var uploadJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = repository.savedList()
            if (saved != null) {
                _uiState.update { it.copy(items = saved, isLoading = false) }
            }
            fetch()
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { fetch() }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = it.items.isEmpty(), error = null) }
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        repository.list()
            .onSuccess { items ->
                _uiState.update {
                    it.copy(
                        items = items,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        showingSavedList = false,
                        offlineNotice = null
                    )
                }
            }
            .onFailure { error ->
                val message = error.message ?: "Couldn't load Aarti & Path. Please try again."
                _uiState.update { state ->
                    if (state.items.isNotEmpty()) {
                        val isNetwork = (error as? AartiPathException)?.isNetwork == true
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            showingSavedList = true,
                            offlineNotice = if (isNetwork) "Offline. Showing saved list." else "Couldn't refresh. Showing saved list."
                        )
                    } else {
                        state.copy(isLoading = false, isRefreshing = false, error = message)
                    }
                }
            }
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    // ---- Add (admin) ----

    fun openAddSheet() = _uiState.update { it.copy(addSheet = it.addSheet ?: AddPdfState()) }

    fun closeAddSheet() {
        uploadJob?.cancel()
        uploadJob = null
        val picked = _uiState.value.addSheet?.picked
        _uiState.update { it.copy(addSheet = null) }
        if (picked != null) viewModelScope.launch { repository.discardPicked(picked) }
    }

    fun onPdfPicked(uri: Uri) {
        val previous = _uiState.value.addSheet ?: AddPdfState()
        _uiState.update { it.copy(addSheet = previous.copy(isInspecting = true, error = null)) }
        viewModelScope.launch {
            repository.inspectPdf(uri)
                .onSuccess { picked ->
                    _uiState.update { state ->
                        val sheet = state.addSheet ?: return@update state
                        val previousAuto = sheet.picked?.let { AartiPathRepository.titleFromFileName(it.displayName) }
                        // Keep a title the admin typed; replace one that was filled in from the old file.
                        val title = if (sheet.title.isBlank() || sheet.title == previousAuto) {
                            AartiPathRepository.titleFromFileName(picked.displayName)
                        } else {
                            sheet.title
                        }
                        state.copy(addSheet = sheet.copy(picked = picked, title = title, isInspecting = false, error = null))
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        val sheet = state.addSheet ?: return@update state
                        state.copy(addSheet = sheet.copy(isInspecting = false, error = error.message))
                    }
                }
        }
    }

    fun setTitle(title: String) = _uiState.update { state ->
        val sheet = state.addSheet ?: return@update state
        state.copy(addSheet = sheet.copy(title = title.take(AartiPathRepository.MAX_TITLE_LENGTH), error = null))
    }

    fun upload() {
        val sheet = _uiState.value.addSheet ?: return
        val picked = sheet.picked ?: return
        val title = sheet.title.trim()
        if (title.isEmpty() || sheet.isUploading) return
        _uiState.update { it.copy(addSheet = sheet.copy(isUploading = true, progress = 0f, error = null)) }
        uploadJob = viewModelScope.launch {
            repository.upload(picked, title) { progress ->
                _uiState.update { state ->
                    val current = state.addSheet ?: return@update state
                    state.copy(addSheet = current.copy(progress = progress))
                }
            }
                .onSuccess { item ->
                    _uiState.update { state ->
                        state.copy(
                            addSheet = null,
                            items = state.items.filterNot { it.id == item.id } + item
                        )
                    }
                    _messages.send("Added ${item.title}")
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        val current = state.addSheet ?: return@update state
                        state.copy(addSheet = current.copy(isUploading = false, progress = 0f, error = error.message))
                    }
                }
            uploadJob = null
        }
    }

    // ---- Delete (admin) ----

    fun askDelete(item: AartiPathItem) = _uiState.update { it.copy(pendingDelete = item) }

    fun cancelDelete() = _uiState.update { if (it.isDeleting) it else it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val item = _uiState.value.pendingDelete ?: return
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            repository.delete(item.id)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.filterNot { it.id == item.id },
                            pendingDelete = null,
                            isDeleting = false
                        )
                    }
                    _messages.send("Deleted ${item.title}")
                }
                .onFailure { error ->
                    _uiState.update { it.copy(pendingDelete = null, isDeleting = false) }
                    _messages.send(error.message ?: "Couldn't delete it. Please try again.")
                }
        }
    }

    override fun onCleared() {
        // A file picked but never uploaded is not needed any more.
        _uiState.value.addSheet?.picked?.let { picked ->
            if (uploadJob == null) picked.file.delete()
        }
    }
}
