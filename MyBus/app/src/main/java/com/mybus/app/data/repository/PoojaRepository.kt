package com.mybus.app.data.repository

import com.mybus.app.data.remote.ApiService
import com.mybus.app.data.remote.dto.*
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoojaRepository @Inject constructor(
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

    suspend fun createPooja(request: CreatePoojaRequest): Result<PoojaDetailData> {
        return try {
            val response = apiService.createPooja(request)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = errorMessage(response, "Failed to create pooja")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getUpcomingPoojas(): Result<List<PoojaListItem>> {
        return try {
            val response = apiService.getUpcomingPoojas()
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data ?: emptyList())
            } else {
                val msg = errorMessage(response, "Failed to load poojas")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getAdminPoojas(): Result<List<PoojaListItem>> {
        return try {
            val response = apiService.getAdminPoojas()
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data ?: emptyList())
            } else {
                val msg = errorMessage(response, "Failed to load poojas")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getPoojaDetail(poojaId: String): Result<PoojaDetailData> {
        return try {
            val response = apiService.getPoojaDetail(poojaId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = errorMessage(response, "Failed to load pooja")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getAdminPoojaDetail(poojaId: String): Result<PoojaDetailData> {
        return try {
            val response = apiService.getAdminPoojaDetail(poojaId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = errorMessage(response, "Failed to load pooja")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun bookToken(poojaId: String, name: String, phone: String): Result<PoojaBookingData> {
        return try {
            val response = apiService.bookPoojaToken(poojaId, BookPoojaRequest(name, phone))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = errorMessage(response, "Failed to book token")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}

