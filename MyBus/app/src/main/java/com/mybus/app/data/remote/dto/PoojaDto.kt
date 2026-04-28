package com.mybus.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreatePoojaRequest(
    @Json(name = "scheduledAt") val scheduledAt: String,
    @Json(name = "place") val place: String,
    @Json(name = "totalTokens") val totalTokens: Int
)

@JsonClass(generateAdapter = true)
data class BookPoojaRequest(
    @Json(name = "name") val name: String,
    @Json(name = "phone") val phone: String
)

@JsonClass(generateAdapter = true)
data class PoojaListItem(
    @Json(name = "id") val id: String,
    @Json(name = "scheduledAt") val scheduledAt: String,
    @Json(name = "place") val place: String,
    @Json(name = "totalTokens") val totalTokens: Int,
    @Json(name = "bookedTokens") val bookedTokens: Int,
    @Json(name = "availableTokens") val availableTokens: Int,
    @Json(name = "status") val status: String,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class PoojaBookingUserInfo(
    @Json(name = "mobile") val mobile: String,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class PoojaBookingData(
    @Json(name = "id") val id: String,
    @Json(name = "poojaId") val poojaId: String,
    @Json(name = "userId") val userId: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "phone") val phone: String,
    @Json(name = "status") val status: String,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "user") val user: PoojaBookingUserInfo? = null
)

@JsonClass(generateAdapter = true)
data class PoojaDetailData(
    @Json(name = "id") val id: String,
    @Json(name = "scheduledAt") val scheduledAt: String,
    @Json(name = "place") val place: String,
    @Json(name = "totalTokens") val totalTokens: Int,
    @Json(name = "bookedTokens") val bookedTokens: Int,
    @Json(name = "availableTokens") val availableTokens: Int,
    @Json(name = "status") val status: String,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "bookings") val bookings: List<PoojaBookingData>? = null
)

