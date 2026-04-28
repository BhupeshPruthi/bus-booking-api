package com.mybus.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EventListItem(
    @Json(name = "id") val id: String,
    @Json(name = "header") val header: String,
    @Json(name = "subHeader") val subHeader: String,
    @Json(name = "eventDate") val eventDate: String,
    @Json(name = "status") val status: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateEventRequest(
    @Json(name = "header") val header: String,
    @Json(name = "subHeader") val subHeader: String,
    @Json(name = "eventDate") val eventDate: String
)

