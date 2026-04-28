package com.mybus.app.data.remote.interceptor

import com.mybus.app.data.remote.ApiBaseUrlHolder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Rewrites request origin (scheme/host/port) to match [ApiBaseUrlHolder], so the same Retrofit
 * build can target emulator (10.0.2.2) or a laptop on LAN (192.168.x.x) without a rebuild.
 */
class DynamicBaseUrlInterceptor @Inject constructor(
    private val apiBaseUrlHolder: ApiBaseUrlHolder
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val targetOrigin = apiBaseUrlHolder.effectiveForInterceptor.toHttpUrlOrNull()
            ?: return chain.proceed(original)
        val newUrl = original.url.newBuilder()
            .scheme(targetOrigin.scheme)
            .host(targetOrigin.host)
            .port(targetOrigin.port)
            .build()
        if (newUrl == original.url) {
            return chain.proceed(original)
        }
        return chain.proceed(
            original.newBuilder().url(newUrl).build()
        )
    }
}
