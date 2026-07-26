package com.example.grossrecipes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

data class Session(
    val serverUrl: String = "",
    val username: String = "",
    val accessToken: String? = null,
    val refreshToken: String? = null
) {
    val isLoggedIn: Boolean get() = !accessToken.isNullOrBlank()
}

class SessionManager(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }

    val sessionFlow: Flow<Session> = context.dataStore.data.map { prefs ->
        Session(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            accessToken = prefs[Keys.ACCESS_TOKEN],
            refreshToken = prefs[Keys.REFRESH_TOKEN]
        )
    }

    suspend fun currentSession(): Session = sessionFlow.first()

    suspend fun saveLogin(serverUrl: String, username: String, accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = serverUrl
            prefs[Keys.USERNAME] = username
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
        }
    }

    /** Swaps in a freshly-refreshed access token without touching anything else. */
    suspend fun updateAccessToken(newAccessToken: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = newAccessToken
        }
    }

    /**
     * Clears the password/token on logout, but keeps server URL + username
     * around (per the README: "server URL/username persist for convenience").
     */
    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
        }
    }
}
