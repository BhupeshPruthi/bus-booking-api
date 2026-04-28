package com.mybus.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TripData(
    @Json(name = "outbound") val outbound: BusDetail,
    @Json(name = "return") val returnTrip: BusDetail? = null
)

@JsonClass(generateAdapter = true)
data class BusDetail(
    @Json(name = "id") val id: String,
    @Json(name = "busName") val busName: String,
    @Json(name = "totalSeats") val totalSeats: Int,
    @Json(name = "price") val price: Double,
    @Json(name = "departureTime") val departureTime: String,
    @Json(name = "arrivalTime") val arrivalTime: String?,
    @Json(name = "contactName") val contactName: String?,
    @Json(name = "contactPhone") val contactPhone: String?,
    @Json(name = "tripType") val tripType: String,
    @Json(name = "status") val status: String,
    @Json(name = "route") val route: RouteDetail,
    @Json(name = "stops") val stops: List<StopDetail>
)

@JsonClass(generateAdapter = true)
data class RouteDetail(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "source") val source: String,
    @Json(name = "destination") val destination: String
)

@JsonClass(generateAdapter = true)
data class StopDetail(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "address") val address: String?,
    @Json(name = "sequence") val sequence: Int
)
