package com.example.grossrecipes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime

private val Context.syncStateDataStore by preferencesDataStore(name = "sync_state")
private val EPOCH: ZonedDateTime = ZonedDateTime.parse("1970-01-01T00:00:00Z")

/**
 * Tracks the "cursor" for event-sourced sync: the time of the last event
 * this phone has already applied. The next sync asks the server for
 * "everything after this," so a fresh install (cursor = epoch) naturally
 * pulls the entire history and bootstraps its local database from scratch -
 * the same mechanism serves both "first login" and "catching up after being
 * offline." Stored as an ISO-8601 string since gross-recipes-common's
 * SyncRequest.lastSync is a real ZonedDateTime, not a raw millis Long.
 */
class SyncStateManager(private val context: Context) {
    private object Keys {
        val LAST_SYNC = stringPreferencesKey("last_sync")
    }

    suspend fun getLastSync(): ZonedDateTime =
        context.syncStateDataStore.data.first()[Keys.LAST_SYNC]?.let { ZonedDateTime.parse(it) } ?: EPOCH

    suspend fun setLastSync(time: ZonedDateTime) {
        context.syncStateDataStore.edit { prefs -> prefs[Keys.LAST_SYNC] = time.toString() }
    }

    /** Called on logout so a different account logging in on this phone starts from zero. */
    suspend fun reset() {
        context.syncStateDataStore.edit { prefs -> prefs[Keys.LAST_SYNC] = EPOCH.toString() }
    }
}
