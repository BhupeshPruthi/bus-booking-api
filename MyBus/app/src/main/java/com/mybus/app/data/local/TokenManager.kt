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
        private val USER_ADMIN_TYPE = stringPreferencesKey("user_admin_type")
        private val USER_CAPABILITIES = stringPreferencesKey("user_capabilities")
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
    val adminType: Flow<String?> = context.dataStore.data.map { it[USER_ADMIN_TYPE] }
    val capabilities: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[USER_CAPABILITIES]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    }

    val debugRoleOverride: Flow<String?> = context.dataStore.data.map {
        it[DEBUG_ROLE_OVERRIDE]?.takeIf { v -> v.isNotBlank() }
    }

    /** Respects [debugRoleOverride] when set (debug builds). */
    val effectiveIsAdmin: Flow<Boolean> = combine(
        userRole,
        isSuperUser,
        adminType,
        debugRoleOverride
    ) { role, superU, type, override ->
        when (override) {
            "admin" -> true
            "consumer" -> false
            else -> superU ||
                type == "super_admin" ||
                type == "bus_admin" ||
                (role == "admin" && type.isNullOrBlank())
        }
    }

    suspend fun readEffectiveIsAdmin(): Boolean = effectiveIsAdmin.first()

    /** Any persisted admin type can read shared areas such as feedback. */
    val isAnyAdmin: Flow<Boolean> = combine(
        userRole,
        isSuperUser,
        adminType,
        debugRoleOverride
    ) { role, superU, type, override ->
        when (override) {
            "admin" -> true
            "consumer" -> false
            else -> superU ||
                role == "admin" ||
                role == "superuser" ||
                type in setOf("bus_admin", "stay_admin", "super_admin")
        }
    }

    val canManageStay: Flow<Boolean> = combine(
        isSuperUser,
        adminType,
        capabilities
    ) { superU, type, caps ->
        superU || type == "super_admin" || type == "stay_admin" || "stay.manage" in caps
    }

    val canManageEvents: Flow<Boolean> = combine(
        isSuperUser,
        adminType,
        capabilities
    ) { superU, type, caps ->
        superU || type == "super_admin" || type == "bus_admin" || "event.manage" in caps
    }

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
        isSuperUserFlag: Boolean = false,
        adminTypeValue: String? = null,
        capabilityValues: List<String> = emptyList()
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
            if (adminTypeValue.isNullOrBlank()) {
                prefs.remove(USER_ADMIN_TYPE)
            } else {
                prefs[USER_ADMIN_TYPE] = adminTypeValue
            }
            if (capabilityValues.isEmpty()) {
                prefs.remove(USER_CAPABILITIES)
            } else {
                prefs[USER_CAPABILITIES] = capabilityValues.joinToString(",")
            }
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
