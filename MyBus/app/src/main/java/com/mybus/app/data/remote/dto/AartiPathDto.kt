package com.mybus.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** One Aarti & Path PDF. Items never change: fixing one means deleting it and adding it again. */
@JsonClass(generateAdapter = true)
data class AartiPathItem(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "pageCount") val pageCount: Int? = null,
    @Json(name = "sizeBytes") val sizeBytes: Long? = null,
    @Json(name = "pdfUrl") val pdfUrl: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AartiPathDeleteResult(
    @Json(name = "id") val id: String? = null
)
