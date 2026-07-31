package com.mybus.app.data.repository

import com.mybus.app.data.local.TokenManager
import com.mybus.app.data.remote.ApiService
import com.mybus.app.data.remote.dto.*
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
) {
    private fun parseErrorMessage(rawError: String?): String? {
        if (rawError.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(rawError)
            json.optJSONObject("error")
                ?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun addGoogleTokenHint(message: String): String {
        if (!message.contains("Invalid Google sign-in token", ignoreCase = true)) {
            return message
        }
        return "$message. The API rejected the Google ID token. Verify Railway GOOGLE_CLIENT_ID " +
            "matches MyBus/local.properties google.web.client.id, then redeploy the API."
    }

    suspend fun signInWithGoogle(idToken: String): Result<LoginData> {
        return try {
            val response = apiService.signInWithGoogle(GoogleSignInRequest(idToken))
            val rawErr = response.errorBody()?.string()
            if (response.isSuccessful && response.body()?.success == true) {
                val loginData = response.body()!!.data!!
                tokenManager.saveTokens(loginData.accessToken, loginData.refreshToken)
                tokenManager.saveUserInfo(
                    id = loginData.user.id,
                    mobile = loginData.user.mobile,
                    email = loginData.user.email,
                    name = loginData.user.name,
                    role = loginData.user.role,
                    isSuperUserFlag = loginData.user.isSuperUser
                )
                Result.success(loginData)
            } else {
                val errBody = response.body()?.error
                val errorMsg = errBody?.message
                    ?: parseErrorMessage(rawErr)
                    ?: rawErr?.take(500)
                    ?: "Google sign-in failed"
                Result.failure(Exception(addGoogleTokenHint(errorMsg)))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            val refreshToken = tokenManager.refreshToken.firstOrNull()
            if (!refreshToken.isNullOrEmpty()) {
                apiService.logout(RefreshTokenRequest(refreshToken))
            }
            tokenManager.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            tokenManager.clear()
            Result.success(Unit)
        }
    }

    suspend fun isLoggedIn(): Boolean {
        return !tokenManager.accessToken.firstOrNull().isNullOrEmpty()
    }
}
