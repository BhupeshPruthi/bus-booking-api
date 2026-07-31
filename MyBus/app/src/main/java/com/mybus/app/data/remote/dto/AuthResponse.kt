package com.mybus.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: T? = null,
    @Json(name = "error") val error: ApiError? = null
)

@JsonClass(generateAdapter = true)
data class ApiError(
    @Json(name = "code") val code: String,
    @Json(name = "message") val message: String
)

@JsonClass(generateAdapter = true)
data class LoginData(
    @Json(name = "accessToken") val accessToken: String,
    @Json(name = "refreshToken") val refreshToken: String,
    @Json(name = "user") val user: UserInfo
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    @Json(name = "id") val id: String,
    @Json(name = "mobile") val mobile: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "role") val role: String,
    @Json(name = "isSuperUser") val isSuperUser: Boolean = false,
    @Json(name = "isNewUser") val isNewUser: Boolean
)

@JsonClass(generateAdapter = true)
data class TokenData(
    @Json(name = "accessToken") val accessToken: String,
    @Json(name = "refreshToken") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class LogoutData(
    @Json(name = "message") val message: String
)
