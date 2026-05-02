package com.mybus.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BusListItem(
    @Json(name = "id") val id: String,
    @Json(name = "routeId") val routeId: String,
    @Json(name = "busName") val busName: String,
    @Json(name = "totalSeats") val totalSeats: Int,
    @Json(name = "seatStartNumber") val seatStartNumber: Int = 1,
    @Json(name = "price") val price: Double,
    @Json(name = "departureTime") val departureTime: String,
    @Json(name = "arrivalTime") val arrivalTime: String?,
    @Json(name = "contactName") val contactName: String?,
    @Json(name = "contactPhone") val contactPhone: String?,
    @Json(name = "tripType") val tripType: String?,
    @Json(name = "returnBusId") val returnBusId: String?,
    @Json(name = "returnDepartureTime") val returnDepartureTime: String? = null,
    @Json(name = "returnArrivalTime") val returnArrivalTime: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "route") val route: BusRouteInfo,
    @Json(name = "bookedSeats") val bookedSeats: Int,
    @Json(name = "availableSeats") val availableSeats: Int,
    @Json(name = "bookableSeats") val bookableSeats: Int? = null
)

@JsonClass(generateAdapter = true)
data class BusRouteInfo(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "source") val source: String,
    @Json(name = "destination") val destination: String
)

@JsonClass(generateAdapter = true)
data class PickupPointInfo(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "address") val address: String?,
    @Json(name = "sequence") val sequence: Int
)

@JsonClass(generateAdapter = true)
data class BusDetailData(
    @Json(name = "id") val id: String,
    @Json(name = "routeId") val routeId: String,
    @Json(name = "busName") val busName: String,
    @Json(name = "totalSeats") val totalSeats: Int,
    @Json(name = "seatStartNumber") val seatStartNumber: Int = 1,
    @Json(name = "price") val price: Double,
    @Json(name = "departureTime") val departureTime: String,
    @Json(name = "arrivalTime") val arrivalTime: String?,
    @Json(name = "contactName") val contactName: String?,
    @Json(name = "contactPhone") val contactPhone: String?,
    @Json(name = "tripType") val tripType: String?,
    @Json(name = "returnBusId") val returnBusId: String?,
    @Json(name = "returnDepartureTime") val returnDepartureTime: String? = null,
    @Json(name = "returnArrivalTime") val returnArrivalTime: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "route") val route: BusRouteInfo,
    @Json(name = "pickupPoints") val pickupPoints: List<PickupPointInfo>,
    @Json(name = "bookedSeats") val bookedSeats: Int,
    @Json(name = "availableSeats") val availableSeats: Int,
    @Json(name = "bookableSeats") val bookableSeats: Int? = null
)

@JsonClass(generateAdapter = true)
data class CreateBookingRequest(
    @Json(name = "busId") val busId: String,
    @Json(name = "pickupPointId") val pickupPointId: String,
    @Json(name = "seatCount") val seatCount: Int,
    @Json(name = "passengerName") val passengerName: String,
    @Json(name = "passengerPhone") val passengerPhone: String
)

@JsonClass(generateAdapter = true)
data class BookingData(
    @Json(name = "id") val id: String,
    @Json(name = "busId") val busId: String? = null,
    @Json(name = "seatCount") val seatCount: Int,
    @Json(name = "assignedSeats") val assignedSeats: String? = null,
    @Json(name = "passengerName") val passengerName: String? = null,
    @Json(name = "passengerPhone") val passengerPhone: String? = null,
    @Json(name = "totalAmount") val totalAmount: Double,
    @Json(name = "status") val status: String,
    @Json(name = "cancellationRequestedAt") val cancellationRequestedAt: String? = null,
    @Json(name = "cancellationReason") val cancellationReason: String? = null,
    @Json(name = "cancellationRejectionReason") val cancellationRejectionReason: String? = null,
    @Json(name = "cancelledAt") val cancelledAt: String? = null,
    @Json(name = "holdExpiresAt") val holdExpiresAt: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "bus") val bus: BookingBusInfo? = null,
    @Json(name = "route") val route: BookingRouteInfo? = null,
    @Json(name = "pickupPoint") val pickupPoint: BookingPickupInfo? = null,
    @Json(name = "user") val user: BookingUserInfo? = null
)

@JsonClass(generateAdapter = true)
data class BookingUserInfo(
    @Json(name = "mobile") val mobile: String? = null,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class BookingBusInfo(
    @Json(name = "name") val name: String,
    @Json(name = "departureTime") val departureTime: String,
    @Json(name = "arrivalTime") val arrivalTime: String? = null,
    @Json(name = "tripType") val tripType: String? = null,
    @Json(name = "returnArrivalTime") val returnArrivalTime: String? = null
)

@JsonClass(generateAdapter = true)
data class BookingRouteInfo(
    @Json(name = "name") val name: String,
    @Json(name = "source") val source: String,
    @Json(name = "destination") val destination: String
)

@JsonClass(generateAdapter = true)
data class BookingPickupInfo(
    @Json(name = "name") val name: String,
    @Json(name = "address") val address: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateBookingStatusRequest(
    @Json(name = "action") val action: String,
    @Json(name = "rejectionReason") val rejectionReason: String? = null
)

@JsonClass(generateAdapter = true)
data class BookingCancellationRequest(
    @Json(name = "reason") val reason: String? = null
)
