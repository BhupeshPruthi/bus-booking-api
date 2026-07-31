package com.mybus.app.data.repository

import com.mybus.app.data.remote.ApiService
import com.mybus.app.data.remote.dto.ApiErrorEnvelope
import com.mybus.app.data.remote.dto.ApiResponse
import com.mybus.app.data.remote.dto.UnifiedBookingItem
import com.mybus.app.data.remote.dto.UnifiedBookingPage
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedBookingRepository @Inject constructor(
    private val apiService: ApiService,
    moshi: Moshi
) {
    private val errorAdapter = moshi.adapter(ApiErrorEnvelope::class.java)

    private fun <T> errorMessage(response: Response<ApiResponse<T>>, fallback: String): String {
        if (response.code() == 401) return "Authentication required"
        val bodyError = response.body()?.error
        val parsedError = response.errorBody()?.string()
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { errorAdapter.fromJson(it)?.error }.getOrNull() }
        val serverMessage = (bodyError ?: parsedError)?.message?.trim()
        if (!serverMessage.isNullOrBlank() &&
            !serverMessage.equals("Internal server error", ignoreCase = true)
        ) {
            return serverMessage
        }
        return when (response.code()) {
            403 -> "You do not have permission to view these bookings."
            404 -> "The booking could not be found."
            in 500..599 -> "$fallback because the booking service had a problem " +
                "(server error ${response.code()}). Please try again."
            else -> "$fallback (error ${response.code()}). Please try again."
        }
    }

    private suspend fun <T> request(
        fallback: String,
        call: suspend () -> Response<ApiResponse<T>>
    ): Result<T> = try {
        val response = call()
        val body = response.body()
        if (response.isSuccessful && body?.success == true && body.data != null) {
            Result.success(body.data)
        } else if (response.code() == 401) {
            Result.failure(AuthenticationRequiredException())
        } else {
            Result.failure(Exception(errorMessage(response, fallback)))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_error: IOException) {
        Result.failure(Exception("$fallback. Check your internet connection and try again."))
    } catch (_error: Exception) {
        Result.failure(Exception("$fallback. The server response could not be read. Please try again."))
    }

    suspend fun getBookings(
        bucket: String,
        types: String? = null,
        cursor: String? = null,
        limit: Int = 20
    ): Result<UnifiedBookingPage> = request("Failed to load your bookings") {
        apiService.getUnifiedBookings(bucket, types, cursor, limit)
    }

    suspend fun getBooking(
        bookingType: String,
        bookingId: String
    ): Result<UnifiedBookingItem> = request("Failed to load booking details") {
        apiService.getUnifiedBooking(bookingType, bookingId)
    }
}
