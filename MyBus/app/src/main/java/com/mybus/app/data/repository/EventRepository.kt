package com.mybus.app.data.repository

import com.mybus.app.data.remote.ApiService
import com.mybus.app.data.remote.dto.CreateEventRequest
import com.mybus.app.data.remote.dto.EventListItem
import com.mybus.app.data.remote.dto.ApiResponse
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val apiService: ApiService
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

    suspend fun createEvent(header: String, subHeader: String, eventDate: String): Result<EventListItem> {
        return try {
            val response = apiService.createEvent(
                CreateEventRequest(header = header, subHeader = subHeader, eventDate = eventDate)
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = errorMessage(response, "Failed to create event")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}

