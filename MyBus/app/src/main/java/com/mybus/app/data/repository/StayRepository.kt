package com.mybus.app.data.repository

import com.mybus.app.data.remote.ApiService
import com.mybus.app.data.remote.dto.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StayRepository @Inject constructor(
    private val apiService: ApiService,
    moshi: Moshi
) {
    private val errorAdapter = moshi.adapter(ApiErrorEnvelope::class.java)

    private fun <T> errorMessage(response: Response<ApiResponse<T>>, fallback: String): String {
        val bodyError = response.body()?.error
        val rawError = response.errorBody()?.string()
        val parsedError = rawError
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { errorAdapter.fromJson(it)?.error }.getOrNull() }
        val apiError = bodyError ?: parsedError
        val detailMessage = apiError?.details?.firstOrNull()?.message?.trim()
        val serverMessage = detailMessage?.let(::friendlyValidationMessage)
            ?: apiError?.message?.trim()

        if (!serverMessage.isNullOrBlank() &&
            !serverMessage.equals("Internal server error", ignoreCase = true)
        ) {
            return serverMessage
        }

        return when (response.code()) {
            401 -> "Your session has expired. Please sign in again."
            403 -> "You do not have permission to perform this Stay action."
            404 -> "$fallback. The requested Stay information was not found."
            in 500..599 -> "$fallback because the Stay service had a problem " +
                "(server error ${response.code()}). Please try again."
            else -> "$fallback (error ${response.code()}). Please try again."
        }
    }

    private fun friendlyValidationMessage(message: String): String = when {
        message.contains("less than or equal to 13") ->
            "Each room or hall category can have at most 13 units."
        else -> message.replace(Regex("^\\\"[^\\\"]+\\\"\\s*"), "")
    }

    private suspend fun <T> request(
        fallback: String,
        call: suspend () -> Response<ApiResponse<T>>
    ): Result<T> = try {
        val response = call()
        val body = response.body()
        if (response.isSuccessful && body?.success == true && body.data != null) {
            Result.success(body.data)
        } else {
            Result.failure(Exception(errorMessage(response, fallback)))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        Result.failure(Exception("$fallback. Check your internet connection and try again."))
    } catch (error: Exception) {
        Result.failure(Exception("$fallback. The server response could not be read. Please try again."))
    }

    suspend fun getCatalog() = request("Failed to load Stay information") {
        apiService.getStayCatalog()
    }

    suspend fun quote(body: StayQuoteRequest) = request("Failed to check availability") {
        apiService.getStayQuote(body)
    }

    suspend fun createBooking(body: CreateStayBookingRequest) =
        request("Failed to submit Stay request") { apiService.createStayBooking(body) }

    suspend fun getMyBookings(page: Int = 1, limit: Int = 50) =
        request("Failed to load your stays") { apiService.getMyStayBookings(page, limit) }

    suspend fun requestCancellation(id: String, reason: String?) =
        request("Failed to request cancellation") {
            apiService.requestStayCancellation(id, StayCancellationRequest(reason))
        }

    suspend fun getAdminBookings(
        status: String? = null,
        search: String? = null,
        page: Int = 1,
        limit: Int = 50
    ) = request("Failed to load Stay bookings") {
        apiService.getAdminStayBookings(status, search, page, limit)
    }

    suspend fun createAdminBooking(body: CreateStayBookingRequest) =
        request("Failed to create Stay booking") { apiService.createAdminStayBooking(body) }

    suspend fun getDailyOccupancy(days: Int = 30) =
        request("Failed to load daily occupancy") {
            apiService.getStayDailyOccupancy(days)
        }

    suspend fun confirmBooking(id: String) = request("Failed to confirm booking") {
        apiService.confirmStayBooking(id)
    }

    suspend fun cancelAdminBooking(
        id: String,
        refundDecision: String,
        refundAmount: Double?,
        reason: String?
    ) = request("Failed to cancel Stay booking") {
        apiService.cancelAdminStayBooking(
            id,
            AdminStayCancellationRequest(refundDecision, refundAmount, reason)
        )
    }

    suspend fun rejectBooking(id: String, reason: String) =
        request("Failed to reject booking") {
            apiService.rejectStayBooking(id, StayRejectionRequest(reason))
        }

    suspend fun getCancellationRequests(
        status: String? = "pending",
        page: Int = 1,
        limit: Int = 50
    ) = request("Failed to load cancellation requests") {
        apiService.getStayCancellationRequests(status, page, limit)
    }

    suspend fun decideCancellation(
        id: String,
        action: String,
        refundDecision: String?,
        refundAmount: Double?,
        reason: String?
    ) = request("Failed to process cancellation") {
        apiService.decideStayCancellation(
            id,
            StayCancellationDecisionRequest(action, refundDecision, refundAmount, reason)
        )
    }

    suspend fun getCoupons() = request("Failed to load coupons") {
        apiService.getStayCoupons()
    }

    suspend fun createCoupon(
        code: String,
        discountAmount: Double,
        startDate: String,
        endDate: String
    ) = request("Failed to create coupon") {
        apiService.createStayCoupon(
            CreateStayCouponRequest(code, discountAmount, startDate, endDate)
        )
    }

    suspend fun deactivateCoupon(id: String) = request("Failed to deactivate coupon") {
        apiService.deactivateStayCoupon(id)
    }

}
