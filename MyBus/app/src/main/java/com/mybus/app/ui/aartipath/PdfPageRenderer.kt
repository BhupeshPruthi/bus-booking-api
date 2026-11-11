package com.mybus.app.ui.aartipath

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Renders pages of one PDF with the platform [PdfRenderer] (no extra library).
 *
 * PdfRenderer allows one open page at a time and isn't thread-safe, so every call runs on one
 * private thread. Rendered pages are kept in a small LRU cache sized by bytes; evicted bitmaps
 * are left to the garbage collector rather than recycled, because a page on screen may still
 * be drawing one.
 */
class PdfPageRenderer private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** Height / width of each page. */
    val pageAspects: List<Float>
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aarti-path-pdf").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()

    @Volatile
    private var closed = false

    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    val pageCount: Int get() = pageAspects.size

    /** The page drawn [widthPx] wide on white, or null if the document is closed or memory is short. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? {
        if (closed || index !in pageAspects.indices || widthPx <= 0) return null
        return try {
            withContext(dispatcher) { renderOnThread(index, widthPx) }
        } catch (_: RejectedExecutionException) {
            null
        }
    }

    private fun renderOnThread(index: Int, requestedWidth: Int): Bitmap? {
        if (closed) return null
        val width = boundedWidth(requestedWidth, pageAspects[index])
        val key = "$index:$width"
        cache.get(key)?.let { return it }
        return try {
            draw(index, width)
        } catch (_: OutOfMemoryError) {
            // Free what we hold and try once more at half the size.
            cache.evictAll()
            runCatching { draw(index, (width / 2).coerceAtLeast(1)) }.getOrNull()
        }?.also { cache.put(key, it) }
    }

    private fun draw(index: Int, width: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            val height = (width * page.height.toFloat() / page.width.toFloat()).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }

    /** Closes the file on the render thread, after any render already running. */
    fun close() {
        if (closed) return
        closed = true
        try {
            executor.execute {
                cache.evictAll()
                runCatching { renderer.close() }
                runCatching { fd.close() }
            }
        } catch (_: RejectedExecutionException) {
            // Already shut down.
        }
        executor.shutdown()
    }

    companion object {
        /** Widest page bitmap; zoomed pages are drawn at most this wide so text stays sharp at 300%. */
        private const val MAX_WIDTH_PX = 2500

        /** Largest single page bitmap (ARGB_8888, 4 bytes a pixel): an A4 page 2,500 px wide. */
        private const val MAX_PAGE_BYTES = 40 * 1024 * 1024

        private fun cacheBytes(): Int {
            val budget = Runtime.getRuntime().maxMemory() / 4
            return budget.coerceIn(24L * 1024 * 1024, 96L * 1024 * 1024).toInt()
        }

        private fun boundedWidth(requested: Int, aspect: Float): Int {
            var width = requested.coerceIn(1, MAX_WIDTH_PX)
            val bytes = width.toLong() * (width * aspect).toLong() * 4
            if (bytes > MAX_PAGE_BYTES) {
                width = (width * sqrt(MAX_PAGE_BYTES.toDouble() / bytes)).toInt().coerceAtLeast(1)
            }
            return width
        }

        /** Opens [file] and reads every page's size. Call off the main thread. */
        fun open(file: File): PdfPageRenderer {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(fd)
                try {
                    val aspects = (0 until renderer.pageCount).map { index ->
                        val page = renderer.openPage(index)
                        try {
                            if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.414f
                        } finally {
                            page.close()
                        }
                    }
                    if (aspects.isEmpty()) throw IllegalStateException("PDF has no pages")
                    return PdfPageRenderer(fd, renderer, aspects)
                } catch (error: Throwable) {
                    runCatching { renderer.close() }
                    throw error
                }
            } catch (error: Throwable) {
                runCatching { fd.close() }
                throw error
            }
        }
    }
}
