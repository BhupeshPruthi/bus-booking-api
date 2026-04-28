package com.mybus.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.apiConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "api_config"
)

@Singleton
class ApiBaseUrlStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Bump key when default API host changes so stale device overrides (e.g. old LAN IPs) are ignored. */
    private val overrideKey = stringPreferencesKey("api_base_url_override_v2")

    suspend fun readOverride(): String? {
        return context.apiConfigDataStore.data
            .map { it[overrideKey] }
            .first()
    }

    suspend fun saveOverride(url: String?) {
        context.apiConfigDataStore.edit { prefs ->
            if (url.isNullOrBlank()) {
                prefs.remove(overrideKey)
            } else {
                prefs[overrideKey] = url
            }
        }
    }
}
