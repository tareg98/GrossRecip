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

/** Just enough to one-tap-refill the login form - never a password. */
data class RecentLogin(val serverUrl: String, val username: String)

class SessionManager(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val RECENT_LOGINS = stringPreferencesKey("recent_logins")
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

    val recentLoginsFlow: Flow<List<RecentLogin>> = context.dataStore.data.map { prefs ->
        parseRecentLogins(prefs[Keys.RECENT_LOGINS] ?: "")
    }

    private fun parseRecentLogins(raw: String): List<RecentLogin> =
        if (raw.isBlank()) emptyList()
        else raw.split("\n").mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                RecentLogin(parts[0], parts[1])
            } else null
        }

    /**
     * Remembers server URL + username so the login screen can offer them as a
     * one-tap shortcut next time - never the password, which is never saved
     * anywhere on the device. Most-recent-first, deduplicated, capped at 5.
     */
    suspend fun rememberLogin(serverUrl: String, username: String) {
        context.dataStore.edit { prefs ->
            val existing = parseRecentLogins(prefs[Keys.RECENT_LOGINS] ?: "")
            val updated = (listOf(RecentLogin(serverUrl, username)) + existing)
                .distinctBy { it.serverUrl to it.username }
                .take(5)
            prefs[Keys.RECENT_LOGINS] = updated.joinToString("\n") { "${it.serverUrl}|${it.username}" }
        }
    }

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
