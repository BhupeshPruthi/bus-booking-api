package com.mybus.app.ui.aartipath

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybus.app.data.repository.AartiPathRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface ReaderState {
    /** First open: downloading. [progress] is 0..1, or null while the size is unknown. */
    data class Downloading(val progress: Float?) : ReaderState
    data class Ready(val pageAspects: List<Float>) : ReaderState
    data class Failed(val message: String) : ReaderState
}

@HiltViewModel
class AartiPathReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AartiPathRepository
) : ViewModel() {

    private val itemId: String = savedStateHandle.get<String>("id").orEmpty()

    val title: String = savedStateHandle.get<String>("title")?.takeIf { it.isNotBlank() }
        ?: repository.item(itemId)?.title
        ?: "Aarti & Path"

    /** Text size chosen in any earlier PDF. */
    val initialZoom: Float = repository.readerZoom()

    fun saveZoom(zoom: Float) = repository.saveReaderZoom(zoom)

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Downloading(null))
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var renderer: PdfPageRenderer? = null
    private var file: File? = null

    init {
        open()
    }

    fun open() {
        viewModelScope.launch {
            _state.value = ReaderState.Downloading(null)
            repository.pdfFile(itemId) { progress -> _state.value = ReaderState.Downloading(progress) }
                .onSuccess { pdf ->
                    try {
                        val opened = withContext(Dispatchers.IO) { PdfPageRenderer.open(pdf) }
                        renderer?.close()
                        renderer = opened
                        file = pdf
                        _state.value = ReaderState.Ready(opened.pageAspects)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        // A damaged saved copy: remove it so Retry downloads it again.
                        repository.forgetDownload(itemId)
                        _state.value = ReaderState.Failed("This PDF can't be opened. Tap Retry to download it again.")
                    }
                }
                .onFailure { error ->
                    _state.value = ReaderState.Failed(error.message ?: "Couldn't open this PDF. Please try again.")
                }
        }
    }

    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? = renderer?.render(index, widthPx)

    /** A copy of the PDF named after its title, ready to share. */
    suspend fun fileForSharing(): File? {
        val pdf = file ?: return null
        return try {
            repository.fileForSharing(pdf, title)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
    }

    override fun onCleared() {
        renderer?.close()
        renderer = null
    }
}
