package com.example.grossrecipes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.syncStateDataStore by preferencesDataStore(name = "sync_state")

/**
 * Tracks the "cursor" for event-sourced sync: the timestamp of the last
 * event this phone has already applied. The next pull asks the server for
 * "everything after this," so a fresh install (cursor = 0) naturally pulls
 * the entire history and bootstraps its local database from scratch - the
 * same mechanism serves both "first login" and "catching up after being
 * offline."
 */
class SyncStateManager(private val context: Context) {
    private object Keys {
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
    }

    suspend fun getLastSyncedAt(): Long =
        context.syncStateDataStore.data.first()[Keys.LAST_SYNCED_AT] ?: 0L

    suspend fun setLastSyncedAt(timestamp: Long) {
        context.syncStateDataStore.edit { prefs -> prefs[Keys.LAST_SYNCED_AT] = timestamp }
    }

    /** Called on logout so a different account logging in on this phone starts from zero. */
    suspend fun reset() {
        context.syncStateDataStore.edit { prefs -> prefs[Keys.LAST_SYNCED_AT] = 0L }
    }
}
