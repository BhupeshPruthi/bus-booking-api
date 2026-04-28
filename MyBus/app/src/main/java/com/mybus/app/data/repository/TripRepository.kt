package com.mybus.app.data.repository

import com.mybus.app.data.remote.ApiService
import com.mybus.app.data.remote.dto.CreateTripRequest
import com.mybus.app.data.remote.dto.TripData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun createTrip(request: CreateTripRequest): Result<TripData> {
        return try {
            val response = apiService.createTrip(request)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val errorMsg = response.body()?.error?.message
                    ?: response.errorBody()?.string()
                    ?: "Failed to create trip"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}
