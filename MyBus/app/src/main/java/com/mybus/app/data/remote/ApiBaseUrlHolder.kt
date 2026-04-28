package com.mybus.app.data.remote

import com.mybus.app.BuildConfig
import com.mybus.app.data.local.ApiBaseUrlStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Effective API base URL for same-network dev: optional override (device) wins over [BuildConfig.API_BASE_URL].
 * [effectiveForInterceptor] is read synchronously by OkHttp; keep in sync on load/save.
 */
@Singleton
class ApiBaseUrlHolder @Inject constructor(
    private val store: ApiBaseUrlStore
) {

    private val _resolvedUrl = MutableStateFlow(normalize(BuildConfig.API_BASE_URL))
    val resolvedUrl: StateFlow<String> = _resolvedUrl.asStateFlow()

    @Volatile
    var effectiveForInterceptor: String = normalize(BuildConfig.API_BASE_URL)
        private set

    suspend fun load() {
        val fromDisk = store.readOverride()
        applyResolved(normalize(fromDisk ?: BuildConfig.API_BASE_URL))
    }

    suspend fun setOverride(url: String?) {
        if (url.isNullOrBlank()) {
            store.saveOverride(null)
            applyResolved(normalize(BuildConfig.API_BASE_URL))
        } else {
            val n = normalize(url.trim())
            store.saveOverride(n)
            applyResolved(n)
        }
    }

    private fun applyResolved(value: String) {
        effectiveForInterceptor = value
        _resolvedUrl.value = value
    }

    companion object {
        fun normalize(url: String): String {
            val t = url.trim()
            return if (t.endsWith("/")) t else "$t/"
        }
    }
}
