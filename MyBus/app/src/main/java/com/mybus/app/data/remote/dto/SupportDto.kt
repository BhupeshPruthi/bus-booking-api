package com.mybus.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HelpContact(
    @Json(name = "id") val id: String,
    @Json(name = "code") val code: String,
    @Json(name = "title") val title: String,
    @Json(name = "contactName") val contactName: String,
    @Json(name = "phone") val phone: String
)

@JsonClass(generateAdapter = true)
data class CreateFeedbackRequest(
    @Json(name = "message") val message: String
)

@JsonClass(generateAdapter = true)
data class FeedbackSubmission(
    @Json(name = "id") val id: String,
    @Json(name = "message") val message: String,
    @Json(name = "createdAt") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class FeedbackSubmitter(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "phone") val phone: String? = null
)

@JsonClass(generateAdapter = true)
data class FeedbackItem(
    @Json(name = "id") val id: String,
    @Json(name = "message") val message: String,
    @Json(name = "createdAt") val createdAt: String,
    @Json(name = "submittedBy") val submittedBy: FeedbackSubmitter
)

@JsonClass(generateAdapter = true)
data class FeedbackPage(
    @Json(name = "items") val items: List<FeedbackItem>,
    @Json(name = "page") val page: Int,
    @Json(name = "limit") val limit: Int,
    @Json(name = "total") val total: Int
)
