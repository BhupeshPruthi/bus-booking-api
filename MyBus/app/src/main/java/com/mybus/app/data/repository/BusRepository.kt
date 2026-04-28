package com.mybus.app.data.repository

import com.mybus.app.data.remote.ApiService
import com.mybus.app.data.remote.dto.*
import javax.inject.Inject
import javax.inject.Singleton

class AuthenticationRequiredException : Exception("Authentication required")

@Singleton
class BusRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun getConsumerBuses(): Result<List<BusListItem>> {
        return try {
            val response = apiService.getBuses()
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = response.body()?.error?.message ?: "Failed to load buses"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getAdminBuses(): Result<List<BusListItem>> {
        return try {
            val response = apiService.getAdminBuses()
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = response.body()?.error?.message ?: "Failed to load buses"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getBusDetail(busId: String): Result<BusDetailData> {
        return try {
            val response = apiService.getBusDetail(busId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = response.body()?.error?.message ?: "Failed to load bus detail"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getBusDetailWithReturnTimes(busId: String): Result<BusDetailData> {
        val result = getBusDetail(busId)
        val bus = result.getOrElse { return Result.failure(it) }
        val returnBusId = bus.returnBusId

        if (
            bus.tripType == "round_trip" &&
            !returnBusId.isNullOrBlank() &&
            bus.returnArrivalTime.isNullOrBlank()
        ) {
            val returnBus = getBusDetail(returnBusId).getOrNull()
            if (returnBus != null) {
                return Result.success(
                    bus.copy(
                        returnDepartureTime = returnBus.departureTime,
                        returnArrivalTime = returnBus.arrivalTime
                    )
                )
            }
        }

        return Result.success(bus)
    }

    suspend fun createBooking(
        busId: String,
        pickupPointId: String,
        seatCount: Int,
        passengerName: String,
        passengerPhone: String
    ): Result<BookingData> {
        return try {
            val response = apiService.createBooking(
                CreateBookingRequest(busId, pickupPointId, seatCount, passengerName, passengerPhone)
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = response.body()?.error?.message ?: "Failed to create booking"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getMyBookings(): Result<List<BookingData>> {
        return try {
            val response = apiService.getMyBookings()
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else if (response.code() == 401) {
                Result.failure(AuthenticationRequiredException())
            } else {
                val msg = response.body()?.error?.message ?: "Failed to load bookings"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getBookingById(bookingId: String): Result<BookingData> {
        return try {
            val response = apiService.getBookingById(bookingId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = response.body()?.error?.message ?: "Failed to load booking"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getAdminBookingsForBus(busId: String): Result<List<BookingData>> {
        return try {
            val response = apiService.getAdminBookings(busId = busId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = response.body()?.error?.message ?: "Failed to load bookings"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun updateBookingStatus(bookingId: String, action: String): Result<BookingData> {
        return try {
            val response = apiService.updateBookingStatus(
                bookingId,
                UpdateBookingStatusRequest(action)
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = response.body()?.error?.message ?: "Failed to update booking"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun requestBookingCancellation(bookingId: String): Result<BookingData> {
        return try {
            val response = apiService.requestBookingCancellation(bookingId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = response.body()?.error?.message ?: "Failed to request cancellation"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun cancelBookingAsAdmin(bookingId: String): Result<BookingData> {
        return try {
            val response = apiService.cancelBookingAsAdmin(bookingId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val msg = response.body()?.error?.message ?: "Failed to cancel booking"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}
