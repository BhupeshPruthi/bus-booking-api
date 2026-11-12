package com.mybus.app.data.repository

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.mybus.app.data.remote.ApiService
import com.mybus.app.data.remote.dto.AartiPathItem
import com.mybus.app.data.remote.dto.ApiErrorEnvelope
import com.mybus.app.data.remote.dto.ApiResponse
import com.mybus.app.data.remote.networkErrorMessage
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A failed Aarti & Path call, with a message that can be shown as is. [isNetwork] is true when
 * the server couldn't be reached at all.
 */
class AartiPathException(message: String, val isNetwork: Boolean = false) : Exception(message)

/** A PDF the admin picked, copied into the app's cache and checked before upload. */
data class PickedPdf(
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int
)

/**
 * Aarti & Path PDFs: the list, downloads kept on the phone for offline reading, and the
 * admin upload and delete calls.
 *
 * Items never change on the server (fixing one means deleting it and adding it again), so a
 * downloaded PDF is kept under its id until the item disappears from a refreshed list. PDFs and
 * the last list live in app storage (noBackupFilesDir), not the cache, so the OS never clears
 * them: a PDF opened once stays readable offline.
 */
@Singleton
class AartiPathRepository @Inject constructor(
    private val apiService: ApiService,
    @ApplicationContext private val context: Context,
    moshi: Moshi
) {
    private val errorAdapter = moshi.adapter(ApiErrorEnvelope::class.java)
    private val listAdapter: JsonAdapter<List<AartiPathItem>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, AartiPathItem::class.java))

    private val pdfDir: File get() = File(context.noBackupFilesDir, PDF_DIR).apply { mkdirs() }
    private val stagingDir: File get() = File(context.cacheDir, STAGING_DIR).apply { mkdirs() }
    private val shareDir: File get() = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
    private val listFile: File get() = File(pdfDir, LIST_FILE)

    @Volatile
    private var legacyCacheCleared = false

    private val _savedIds = MutableStateFlow<Set<String>>(emptySet())

    /** Ids of PDFs already on the phone. */
    val savedIds: StateFlow<Set<String>> = _savedIds.asStateFlow()

    @Volatile
    private var lastItems: List<AartiPathItem> = emptyList()
    private val downloadMutex = Mutex()

    /** The item from the most recent list, if loaded. */
    fun item(id: String): AartiPathItem? = lastItems.firstOrNull { it.id == id }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /** Reader text size as a zoom factor over fit-width (1 = 100%), the same for every PDF. */
    fun readerZoom(): Float = prefs.getFloat(READER_ZOOM_KEY, 1f)

    fun saveReaderZoom(zoom: Float) {
        prefs.edit().putFloat(READER_ZOOM_KEY, zoom).apply()
    }

    /** The list saved on the phone the last time it loaded, so saved PDFs open offline. */
    suspend fun savedList(): List<AartiPathItem>? = withContext(Dispatchers.IO) {
        clearLegacyCacheOnce()
        refreshSavedIds()
        val items = runCatching { listFile.takeIf { it.exists() }?.readText()?.let(listAdapter::fromJson) }
            .getOrNull()
        if (items != null && lastItems.isEmpty()) lastItems = items
        items
    }

    suspend fun list(): Result<List<AartiPathItem>> = try {
        val response = apiService.getAartiPathItems()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            val items = body.data.orEmpty()
            lastItems = items
            withContext(Dispatchers.IO) {
                saveList(items)
                pruneDownloads(items.mapTo(HashSet()) { it.id })
                refreshSavedIds()
            }
            Result.success(items)
        } else {
            Result.failure(AartiPathException(errorMessage(response, "Couldn't load Aarti & Path")))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        Result.failure(AartiPathException(networkErrorMessage(error), isNetwork = true))
    } catch (error: Exception) {
        Result.failure(AartiPathException(networkErrorMessage(error)))
    }

    /**
     * The PDF on the phone, downloading it first if needed. [onProgress] gets 0..1, or null when
     * the size is unknown. A saved PDF opens without any network call.
     */
    suspend fun pdfFile(id: String, onProgress: (Float?) -> Unit = {}): Result<File> = try {
        val cached = downloadedFile(id)
        if (cached.isFile && cached.length() > 0) {
            Result.success(cached)
        } else {
            downloadMutex.withLock {
                if (cached.isFile && cached.length() > 0) cached else download(id, cached, onProgress)
            }.let { Result.success(it) }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: AartiPathException) {
        Result.failure(error)
    } catch (error: IOException) {
        Result.failure(AartiPathException(FIRST_OPEN_OFFLINE_MESSAGE, isNetwork = true))
    } catch (error: Exception) {
        Result.failure(AartiPathException("Couldn't open this PDF. Please try again."))
    }

    /** Removes a saved PDF that turned out to be unreadable, so the next open downloads it again. */
    suspend fun forgetDownload(id: String) = withContext(Dispatchers.IO) {
        downloadedFile(id).delete()
        refreshSavedIds()
    }

    /** A copy named after the title, for sharing (the saved file is named by id). */
    suspend fun fileForSharing(source: File, title: String): File = withContext(Dispatchers.IO) {
        shareDir.listFiles()?.forEach { it.delete() }
        val target = File(shareDir, "${safeFileName(title)}.pdf")
        source.copyTo(target, overwrite = true)
        target
    }

    /** Copies the picked PDF into the cache and checks its size and that it opens. */
    suspend fun inspectPdf(uri: Uri): Result<PickedPdf> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            var displayName: String? = null
            var reportedSize: Long? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) reportedSize = cursor.getLong(sizeIndex)
                    }
                }
            reportedSize?.let { if (it > MAX_UPLOAD_BYTES) return@withContext Result.failure(tooLarge(it)) }

            stagingDir.listFiles()?.forEach { it.delete() }
            val staged = File(stagingDir, "picked-${System.currentTimeMillis()}.pdf")
            val input = resolver.openInputStream(uri)
                ?: return@withContext Result.failure(AartiPathException("Couldn't read this file. Please choose it again."))
            var copied = 0L
            input.use { source ->
                staged.outputStream().use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        copied += read
                        if (copied > MAX_UPLOAD_BYTES) {
                            out.close()
                            staged.delete()
                            return@withContext Result.failure(tooLarge(copied))
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }

            val pageCount = runCatching {
                ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { it.pageCount }
                }
            }.getOrNull()
            if (pageCount == null || pageCount <= 0 || !hasPdfHeader(staged)) {
                staged.delete()
                return@withContext Result.failure(AartiPathException("This file can't be opened as a PDF."))
            }

            Result.success(
                PickedPdf(
                    file = staged,
                    displayName = displayName?.takeIf { it.isNotBlank() } ?: "Document.pdf",
                    sizeBytes = copied,
                    pageCount = pageCount
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(AartiPathException("Couldn't read this file. Please choose it again."))
        }
    }

    /** Deletes a picked file that was not uploaded. */
    suspend fun discardPicked(picked: PickedPdf) = withContext(Dispatchers.IO) { picked.file.delete() }

    suspend fun upload(
        picked: PickedPdf,
        title: String,
        onProgress: (Float) -> Unit
    ): Result<AartiPathItem> = try {
        val fileBody = ProgressFileRequestBody(picked.file, PDF_MEDIA_TYPE, onProgress)
        val response = apiService.uploadAartiPath(
            title = title.toRequestBody(TEXT_MEDIA_TYPE),
            pageCount = picked.pageCount.toString().toRequestBody(TEXT_MEDIA_TYPE),
            file = MultipartBody.Part.createFormData("file", uploadFileName(picked.displayName), fileBody)
        )
        val body = response.body()
        if (response.isSuccessful && body?.success == true && body.data != null) {
            val item = body.data
            lastItems = lastItems + item
            withContext(Dispatchers.IO) {
                // The admin's phone already has this PDF: keep it as the saved copy.
                val target = downloadedFile(item.id)
                if (!picked.file.renameTo(target)) {
                    runCatching { picked.file.copyTo(target, overwrite = true) }
                    picked.file.delete()
                }
                saveList(lastItems)
                refreshSavedIds()
            }
            Result.success(item)
        } else {
            Result.failure(AartiPathException(errorMessage(response, "Couldn't upload the PDF")))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        Result.failure(AartiPathException(networkErrorMessage(error), isNetwork = true))
    } catch (error: Exception) {
        Result.failure(AartiPathException(networkErrorMessage(error)))
    }

    suspend fun delete(id: String): Result<Unit> = try {
        val response = apiService.deleteAartiPath(id)
        // 404: someone else already deleted it, which is what we wanted.
        if ((response.isSuccessful && response.body()?.success == true) || response.code() == 404) {
            lastItems = lastItems.filterNot { it.id == id }
            withContext(Dispatchers.IO) {
                downloadedFile(id).delete()
                saveList(lastItems)
                refreshSavedIds()
            }
            Result.success(Unit)
        } else {
            Result.failure(AartiPathException(errorMessage(response, "Couldn't delete it")))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        Result.failure(AartiPathException(networkErrorMessage(error), isNetwork = true))
    } catch (error: Exception) {
        Result.failure(AartiPathException(networkErrorMessage(error)))
    }

    private suspend fun download(id: String, target: File, onProgress: (Float?) -> Unit): File =
        withContext(Dispatchers.IO) {
            val response = apiService.downloadAartiPathPdf(id)
            if (!response.isSuccessful) {
                val raw = response.errorBody()?.use { it.string() }
                val message = if (response.code() == 404) {
                    "This PDF was removed. Go back and pull down to refresh the list."
                } else {
                    errorMessage(response.code(), raw, "Couldn't download the PDF")
                }
                throw AartiPathException(message)
            }
            val body = response.body() ?: throw AartiPathException("Couldn't download the PDF. Please try again.")
            val total = body.contentLength().takeIf { it > 0 } ?: item(id)?.sizeBytes ?: -1L
            val partial = File(target.parentFile, "${target.name}.part")
            body.use { responseBody ->
                responseBody.byteStream().use { input ->
                    partial.outputStream().use { out ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var done = 0L
                        var lastPercent = -1
                        onProgress(if (total > 0) 0f else null)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            done += read
                            if (total > 0) {
                                val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                    }
                }
            }
            if (!hasPdfHeader(partial)) {
                partial.delete()
                throw AartiPathException("The downloaded file isn't a PDF. Please try again.")
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            refreshSavedIds()
            target
        }

    private fun downloadedFile(id: String) = File(pdfDir, "${safeId(id)}.pdf")

    /** Writes the list through a temp file, so a crash never leaves a half-written list. */
    private fun saveList(items: List<AartiPathItem>) {
        runCatching {
            val temp = File(pdfDir, "$LIST_FILE.tmp")
            temp.writeText(listAdapter.toJson(items))
            if (!temp.renameTo(listFile)) {
                temp.copyTo(listFile, overwrite = true)
                temp.delete()
            }
        }
    }

    private fun refreshSavedIds() {
        _savedIds.value = pdfDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".pdf") && it.length() > 0 }
            ?.mapTo(HashSet()) { it.name.removeSuffix(".pdf") }
            .orEmpty()
    }

    /**
     * Deletes saved PDFs (and unfinished downloads) whose item is no longer in the list, i.e. an
     * admin deleted it. The saved list itself is kept.
     */
    private fun pruneDownloads(keepIds: Set<String>) {
        val keepNames = keepIds.mapTo(HashSet()) { "${safeId(it)}.pdf" }
        pdfDir.listFiles()?.forEach { file ->
            val pdfName = file.name.removeSuffix(".part")
            if (pdfName.endsWith(".pdf") && pdfName !in keepNames) file.delete()
        }
    }

    /** Early builds kept PDFs in the cache folder; remove that copy once. */
    private fun clearLegacyCacheOnce() {
        if (legacyCacheCleared) return
        legacyCacheCleared = true
        runCatching {
            File(context.cacheDir, PDF_DIR).deleteRecursively()
            File(context.cacheDir, "aarti-path-list.json").delete()
        }
    }

    private fun hasPdfHeader(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(5)
            input.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
        }
    }.getOrDefault(false)

    private fun tooLarge(bytes: Long) = AartiPathException(
        "This PDF is ${formatPdfSize(bytes)}. The limit is 20 MB."
    )

    private fun <T> errorMessage(response: Response<ApiResponse<T>>, fallback: String): String {
        val bodyMessage = response.body()?.error?.message
        if (!bodyMessage.isNullOrBlank()) return bodyMessage
        return errorMessage(response.code(), response.errorBody()?.use { it.string() }, fallback)
    }

    private fun errorMessage(httpStatus: Int, raw: String?, fallback: String): String {
        if (httpStatus == 413) return "This PDF is too large. The limit is 20 MB."
        if (httpStatus == 401) return "Please log in again to make changes."
        if (raw.isNullOrBlank()) return "$fallback. Please try again."
        val error = runCatching { errorAdapter.fromJson(raw)?.error }.getOrNull()
        val message = error?.message
            ?: Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
        val detail = error?.details?.firstOrNull()?.message
        return when {
            message == "Validation failed" && !detail.isNullOrBlank() -> detail
            !message.isNullOrBlank() && message != "Internal server error" -> message
            else -> "$fallback. Please try again."
        }
    }

    /** Streams a file and reports how much has been sent. Can be written more than once (auth retry). */
    private class ProgressFileRequestBody(
        private val file: File,
        private val mediaType: MediaType,
        private val onProgress: (Float) -> Unit
    ) : RequestBody() {
        override fun contentType(): MediaType = mediaType
        override fun contentLength(): Long = file.length()
        override fun writeTo(sink: BufferedSink) {
            val total = file.length().coerceAtLeast(1L)
            var sent = 0L
            var lastPercent = -1
            onProgress(0f)
            file.source().use { source ->
                while (true) {
                    val read = source.read(sink.buffer, UPLOAD_CHUNK)
                    if (read == -1L) break
                    sink.flush()
                    sent += read
                    val percent = ((sent * 100) / total).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent / 100f)
                    }
                }
            }
        }
    }

    companion object {
        /** Matches the server's limit. */
        const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024
        const val MAX_TITLE_LENGTH = 80

        const val FIRST_OPEN_OFFLINE_MESSAGE = "Connect to the internet to open this the first time."

        private const val PREFS_NAME = "aarti_path"
        private const val READER_ZOOM_KEY = "aarti_path_reader_zoom"
        private const val PDF_DIR = "aarti-path"
        private const val STAGING_DIR = "aarti-path-upload"
        /** Under the FileProvider's `exports/` path. */
        private const val SHARE_DIR = "exports/aarti-path"
        private const val LIST_FILE = "list.json"
        private const val BUFFER_SIZE = 64 * 1024
        private const val UPLOAD_CHUNK = 64L * 1024
        private val PDF_MEDIA_TYPE = "application/pdf".toMediaType()
        private val TEXT_MEDIA_TYPE = "text/plain".toMediaType()

        private fun safeId(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

        private fun safeFileName(title: String): String =
            title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_TITLE_LENGTH)
                .ifBlank { "Aarti and Path" }

        private fun uploadFileName(displayName: String): String {
            val base = safeFileName(displayName.replace(Regex("(?i)\\.pdf$"), ""))
            return "$base.pdf"
        }

        /** "Hanuman_Chalisa-hindi.pdf" → "Hanuman Chalisa hindi". */
        fun titleFromFileName(displayName: String): String {
            val cleaned = displayName
                .replace(Regex("(?i)\\.pdf$"), "")
                .replace(Regex("[_-]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_TITLE_LENGTH)
            return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}

/** "93 KB", "1.3 MB". */
fun formatPdfSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return if (kb < 1024) {
        "${kb.coerceAtLeast(1.0).toInt()} KB"
    } else {
        val mb = kb / 1024.0
        if (mb >= 10) "${mb.toInt()} MB" else String.format(Locale.US, "%.1f MB", mb)
    }
}
