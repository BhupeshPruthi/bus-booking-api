package com.mybus.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chalisaDataStore: DataStore<Preferences> by preferencesDataStore(name = "chalisa_prefs")

/**
 * Hanuman Chalisa settings kept on this phone. The guest pass lets someone who isn't signed in
 * keep counting under the same name; it isn't cleared on logout because it belongs to the phone.
 */
@Singleton
class ChalisaStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val GUEST_TOKEN = stringPreferencesKey("guest_token")
        private val LOCK_SCREEN_COUNTER = booleanPreferencesKey("lock_screen_counter")
        private val LAST_TAP_ID = stringPreferencesKey("last_tap_id")
        private val LAST_TAP_UNDO_UNTIL = stringPreferencesKey("last_tap_undo_until")
    }

    val guestToken: Flow<String?> = context.chalisaDataStore.data.map { it[GUEST_TOKEN] }
    val lockScreenCounter: Flow<Boolean> = context.chalisaDataStore.data.map { it[LOCK_SCREEN_COUNTER] == true }

    suspend fun currentGuestToken(): String? = guestToken.first()

    suspend fun saveGuestToken(token: String) {
        context.chalisaDataStore.edit { it[GUEST_TOKEN] = token }
    }

    suspend fun clearGuestToken() {
        context.chalisaDataStore.edit { it.remove(GUEST_TOKEN) }
    }

    suspend fun setLockScreenCounter(enabled: Boolean) {
        context.chalisaDataStore.edit { it[LOCK_SCREEN_COUNTER] = enabled }
    }

    suspend fun isLockScreenCounterOn(): Boolean = lockScreenCounter.first()

    /** The latest tap, so the lock-screen Undo knows what to remove. */
    suspend fun saveLastTap(clientRequestId: String, undoUntil: String) {
        context.chalisaDataStore.edit {
            it[LAST_TAP_ID] = clientRequestId
            it[LAST_TAP_UNDO_UNTIL] = undoUntil
        }
    }

    suspend fun lastTap(): Pair<String, String>? {
        val prefs = context.chalisaDataStore.data.first()
        val id = prefs[LAST_TAP_ID] ?: return null
        val until = prefs[LAST_TAP_UNDO_UNTIL] ?: return null
        return id to until
    }

    suspend fun clearLastTap() {
        context.chalisaDataStore.edit {
            it.remove(LAST_TAP_ID)
            it.remove(LAST_TAP_UNDO_UNTIL)
        }
    }
}
