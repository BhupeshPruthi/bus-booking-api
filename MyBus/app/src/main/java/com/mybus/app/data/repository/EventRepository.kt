package com.mybus.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.mybus.app.data.remote.ApiService
import com.mybus.app.data.remote.dto.CreateEventRequest
import com.mybus.app.data.remote.dto.EventListItem
import com.mybus.app.data.remote.dto.ApiResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val apiService: ApiService,
    @ApplicationContext private val context: Context
) {
    private fun <T> errorMessage(response: Response<ApiResponse<T>>, fallback: String): String {
        val bodyMsg = response.body()?.error?.message
        if (!bodyMsg.isNullOrBlank()) return bodyMsg

        val raw = response.errorBody()?.string()
        if (raw.isNullOrBlank()) return fallback

        val extracted = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"")
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)

        return extracted ?: raw
    }

    suspend fun getUpcomingEvents(): Result<List<EventListItem>> {
        return try {
            val response = apiService.getUpcomingEvents()
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data ?: emptyList())
            } else {
                val msg = errorMessage(response, "Failed to load events")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun createEvent(
        header: String,
        subHeader: String,
        eventDate: String,
        imageUri: Uri? = null
    ): Result<EventListItem> {
        return try {
            val response = if (imageUri == null) {
                apiService.createEvent(
                    CreateEventRequest(header = header, subHeader = subHeader, eventDate = eventDate)
                )
            } else {
                apiService.createEventMultipart(
                    header = header.toPlainTextBody(),
                    subHeader = subHeader.toPlainTextBody(),
                    eventDate = eventDate.toPlainTextBody(),
                    image = createImagePart(imageUri)
                )
            }
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = errorMessage(response, "Failed to create event")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to create event"))
        }
    }

    private fun String.toPlainTextBody() = toRequestBody("text/plain".toMediaType())

    private fun createImagePart(uri: Uri): MultipartBody.Part {
        val bytes = compressEventImage(uri)
        val body = bytes.toRequestBody("image/jpeg".toMediaType())
        return MultipartBody.Part.createFormData(
            name = "image",
            filename = "event_${System.currentTimeMillis()}.jpg",
            body = body
        )
    }

    private fun compressEventImage(uri: Uri): ByteArray {
        val bitmap = decodeScaledBitmap(uri)
        return ByteArrayOutputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, EVENT_IMAGE_JPEG_QUALITY, output)) {
                throw IllegalArgumentException("Could not prepare selected image")
            }
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun decodeScaledBitmap(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return decodeScaledBitmapWithImageDecoder(uri)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: throw IllegalArgumentException("Could not read selected image")

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Selected file is not a valid image")
        }

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > EVENT_IMAGE_MAX_DIMENSION) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IllegalArgumentException("Could not read selected image")

        val maxDimension = maxOf(bitmap.width, bitmap.height)
        if (maxDimension <= EVENT_IMAGE_MAX_DIMENSION) return bitmap

        val scale = EVENT_IMAGE_MAX_DIMENSION.toFloat() / maxDimension.toFloat()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun decodeScaledBitmapWithImageDecoder(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val maxDimension = maxOf(width, height)
            if (maxDimension > EVENT_IMAGE_MAX_DIMENSION) {
                val scale = EVENT_IMAGE_MAX_DIMENSION.toFloat() / maxDimension.toFloat()
                val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
                val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
                decoder.setTargetSize(targetWidth, targetHeight)
            }
        }
    }

    private companion object {
        const val EVENT_IMAGE_MAX_DIMENSION = 1600
        const val EVENT_IMAGE_JPEG_QUALITY = 85
    }
}
