package com.mybus.app.data.remote.interceptor

import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.ApiBaseUrlHolder
import com.mybus.app.data.remote.dto.ApiResponse
import com.mybus.app.data.remote.dto.RefreshTokenRequest
import com.mybus.app.data.remote.dto.TokenData
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Provider

class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val moshi: Moshi,
    private val apiBaseUrlHolder: ApiBaseUrlHolder
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val refreshToken = runBlocking { tokenManager.refreshToken.firstOrNull() } ?: return null

        val newTokens = runBlocking { refreshAccessToken(refreshToken) } ?: return null

        runBlocking {
            tokenManager.saveTokens(newTokens.accessToken, newTokens.refreshToken)
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${newTokens.accessToken}")
            .build()
    }

    private fun refreshAccessToken(refreshToken: String): TokenData? {
        return try {
            val body = moshi.adapter(RefreshTokenRequest::class.java)
                .toJson(RefreshTokenRequest(refreshToken))
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${apiBaseUrlHolder.effectiveForInterceptor}auth/refresh-token")
                .post(body)
                .build()

            val client = OkHttpClient.Builder().build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return null
                val type = com.squareup.moshi.Types.newParameterizedType(
                    ApiResponse::class.java, TokenData::class.java
                )
                val adapter = moshi.adapter<ApiResponse<TokenData>>(type)
                val apiResponse = adapter.fromJson(responseBody)
                apiResponse?.data
            } else {
                runBlocking { tokenManager.clear() }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
