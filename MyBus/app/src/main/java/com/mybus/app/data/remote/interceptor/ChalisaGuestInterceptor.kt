package com.mybus.app.data.remote.interceptor

import com.mybus.app.data.local.ChalisaStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Sends the Hanuman Chalisa guest pass with Chalisa requests. The server prefers the sign-in
 * token when both are present, so signed-in users are never counted as guests.
 */
class ChalisaGuestInterceptor @Inject constructor(
    private val chalisaStore: ChalisaStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.contains("/chalisa/")) return chain.proceed(request)

        val guestToken = runBlocking { chalisaStore.currentGuestToken() }
        if (guestToken.isNullOrBlank()) return chain.proceed(request)

        return chain.proceed(request.newBuilder().header(GUEST_HEADER, guestToken).build())
    }

    companion object {
        const val GUEST_HEADER = "X-Chalisa-Guest"
    }
}
