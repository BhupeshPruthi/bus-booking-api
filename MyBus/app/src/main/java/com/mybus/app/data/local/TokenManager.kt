package com.mybus.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mybus_prefs")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val USER_ID = stringPreferencesKey("user_id")
        private val USER_MOBILE = stringPreferencesKey("user_mobile")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_ROLE = stringPreferencesKey("user_role")
        private val USER_IS_SUPERUSER = booleanPreferencesKey("user_is_superuser")
        /** Debug only: `"admin"` | `"consumer"` — empty = use server role */
        private val DEBUG_ROLE_OVERRIDE = stringPreferencesKey("debug_role_override")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[REFRESH_TOKEN] }
    val userId: Flow<String?> = context.dataStore.data.map { it[USER_ID] }
    val userMobile: Flow<String?> = context.dataStore.data.map { it[USER_MOBILE] }
    val userEmail: Flow<String?> = context.dataStore.data.map { it[USER_EMAIL] }
    val userName: Flow<String?> = context.dataStore.data.map { it[USER_NAME] }
    val userRole: Flow<String?> = context.dataStore.data.map { it[USER_ROLE] }
    val isSuperUser: Flow<Boolean> = context.dataStore.data.map { it[USER_IS_SUPERUSER] == true }

    val debugRoleOverride: Flow<String?> = context.dataStore.data.map {
        it[DEBUG_ROLE_OVERRIDE]?.takeIf { v -> v.isNotBlank() }
    }

    /** Respects [debugRoleOverride] when set (debug builds). */
    val effectiveIsAdmin: Flow<Boolean> = combine(
        userRole,
        isSuperUser,
        debugRoleOverride
    ) { role, superU, override ->
        when (override) {
            "admin" -> true
            "consumer" -> false
            else -> role == "admin" || role == "superuser" || superU
        }
    }

    suspend fun readEffectiveIsAdmin(): Boolean = effectiveIsAdmin.first()

    suspend fun setDebugRoleOverride(mode: String?) {
        context.dataStore.edit { prefs ->
            if (mode.isNullOrBlank()) prefs.remove(DEBUG_ROLE_OVERRIDE)
            else prefs[DEBUG_ROLE_OVERRIDE] = mode
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = accessToken
            prefs[REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun saveUserInfo(
        id: String,
        mobile: String?,
        email: String?,
        name: String?,
        role: String,
        isSuperUserFlag: Boolean = false
    ) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID] = id
            if (mobile.isNullOrBlank()) {
                prefs.remove(USER_MOBILE)
            } else {
                prefs[USER_MOBILE] = mobile
            }
            if (email.isNullOrBlank()) {
                prefs.remove(USER_EMAIL)
            } else {
                prefs[USER_EMAIL] = email
            }
            name?.let { prefs[USER_NAME] = it }
            prefs[USER_ROLE] = role
            prefs[USER_IS_SUPERUSER] = isSuperUserFlag
        }
    }

    suspend fun updateUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_NAME] = name
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
