package com.mybus.app.data.repository

import com.mybus.app.data.remote.ApiService
import com.mybus.app.data.remote.dto.ApiErrorEnvelope
import com.mybus.app.data.remote.dto.ApiResponse
import com.mybus.app.data.remote.dto.CreateFeedbackRequest
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupportRepository @Inject constructor(
    private val apiService: ApiService,
    moshi: Moshi
) {
    private val errorAdapter = moshi.adapter(ApiErrorEnvelope::class.java)

    private fun <T> errorMessage(response: Response<ApiResponse<T>>, fallback: String): String {
        val parsedError = response.errorBody()?.string()
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { errorAdapter.fromJson(it)?.error }.getOrNull() }
        val serverMessage = (response.body()?.error ?: parsedError)?.message?.trim()
        if (!serverMessage.isNullOrBlank() &&
            !serverMessage.equals("Internal server error", ignoreCase = true)
        ) {
            return serverMessage
        }
        return when (response.code()) {
            401 -> "Your session has expired. Please sign in again."
            403 -> "You do not have permission to view feedback."
            in 500..599 -> "$fallback because the server had a problem. Please try again."
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

    suspend fun submitFeedback(message: String) = request("Failed to send feedback") {
        apiService.submitFeedback(CreateFeedbackRequest(message.trim()))
    }

    suspend fun getFeedback(page: Int = 1, limit: Int = 20) =
        request("Failed to load feedback") { apiService.getAdminFeedback(page, limit) }

    suspend fun getHelpContacts() = request("Failed to load Help contacts") {
        apiService.getHelpContacts()
    }
}
