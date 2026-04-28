package com.mybus.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StopRequest(
    @Json(name = "name") val name: String,
    @Json(name = "address") val address: String?
)

@JsonClass(generateAdapter = true)
data class CreateTripRequest(
    @Json(name = "source") val source: String,
    @Json(name = "destination") val destination: String,
    @Json(name = "departureTime") val departureTime: String,
    @Json(name = "arrivalTime") val arrivalTime: String,
    @Json(name = "totalSeats") val totalSeats: Int,
    @Json(name = "seatStartNumber") val seatStartNumber: Int = 1,
    @Json(name = "price") val price: Double,
    @Json(name = "contactName") val contactName: String,
    @Json(name = "contactPhone") val contactPhone: String,
    @Json(name = "tripType") val tripType: String,
    @Json(name = "stops") val stops: List<StopRequest>,
    @Json(name = "returnDepartureTime") val returnDepartureTime: String? = null,
    @Json(name = "returnArrivalTime") val returnArrivalTime: String? = null
)
