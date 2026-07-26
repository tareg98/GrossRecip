package com.example.grossrecipes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.deviceIdDataStore by preferencesDataStore(name = "device_id")

/**
 * A random id generated once per install and never changed. The server uses
 * this to recognize "this event came from this phone" so it can leave the
 * phone's own events out of what it sends back on the next pull (the phone
 * already applied them locally the moment they were created, so re-receiving
 * them would just be wasted, harmless work - but skipping them is cleaner).
 */
class DeviceIdProvider(private val context: Context) {
    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    suspend fun getOrCreate(): String {
        val existing = context.deviceIdDataStore.data.first()[Keys.DEVICE_ID]
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        context.deviceIdDataStore.edit { prefs -> prefs[Keys.DEVICE_ID] = newId }
        return newId
    }
}
