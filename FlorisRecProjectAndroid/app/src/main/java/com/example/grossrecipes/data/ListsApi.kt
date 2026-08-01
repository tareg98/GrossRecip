package com.example.grossrecipes.data

import com.sirolf2009.grossrecipes.sync.dto.SyncRequest
import com.sirolf2009.grossrecipes.sync.dto.SyncResponse
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Event-sourced sync: instead of one REST call per action (create list, set
 * checked, etc.), there's exactly one call - send what happened here, get
 * back what happened everywhere else, in the same round trip. Uses
 * gross-recipes-common's own SyncRequest/SyncResponse types directly so the
 * wire format matches your friend's side exactly. See ListsRepository for how
 * events are built and applied.
 */
interface ListsApi {

    @POST("Events/sync")
    suspend fun sync(@Body request: SyncRequest): Response<SyncResponse>
}

/**
 * [sessionManager] lets any client built with this auto-recover from an
 * expired access token: on a 401/498, it calls the real /Account/refresh
 * endpoint using the separately-stored, longer-lived refresh token (NOT the
 * access token that just got rejected - the whole point of a refresh token
 * is that it outlives the access token, so it's still good long after the
 * access token has expired) and retries the failed request once with the new
 * access token. If refresh itself fails, the session is cleared - the user
 * has to log in again next time they open the app.
 *
 * This is a plain Interceptor, not OkHttp's built-in Authenticator - the
 * Authenticator hook only ever fires automatically for a real 401. This
 * backend replies with 498 for an invalid/expired token instead, which
 * Authenticator has no idea what to do with, so it silently never ran and
 * the request just failed with the raw 498. An Interceptor sees every status
 * code, so it can check for both itself.
 *
 * Shared by [createListsApi] and [observeSyncSignal] (see SseClient.kt) so
 * both a normal sync call and the SSE connection recover from an expired
 * token the exact same way, instead of two copies of this logic slowly
 * drifting apart.
 */
fun authRefreshInterceptor(serverUrl: String, accessToken: String, sessionManager: SessionManager): Interceptor =
    Interceptor { chain ->
        val currentToken = runBlocking { sessionManager.currentSession().accessToken } ?: accessToken
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $currentToken")
            .build()
        val response = chain.proceed(request)

        if (response.code != 401 && response.code != 498) {
            return@Interceptor response
        }

        // Only try once per request - if a freshly-refreshed token still
        // gets rejected, something else is wrong and we shouldn't loop.
        // response isn't closed yet here - if refresh fails, it gets
        // returned as-is below so its body is still readable (that's
        // what ListsRepository reads to show the real failure reason).
        val newToken = runBlocking {
            try {
                val refreshToken = sessionManager.currentSession().refreshToken
                    ?: return@runBlocking null
                val accountApi = createAccountApi(serverUrl, refreshToken)
                val refreshResponse = accountApi.refresh()
                if (!refreshResponse.isSuccessful) return@runBlocking null
                val bodyText = refreshResponse.body()?.string() ?: return@runBlocking null
                // parseServerString unwraps the same {"value": "..."}
                // envelope discovered on register's response - if refresh
                // turns out wrapped the same way, the old raw
                // Gson().fromJson(bodyText, String::class.java) here would
                // throw on a JSON object, get swallowed below, and look
                // exactly like a failed refresh even with a perfectly
                // good new token sitting in the body.
                val refreshed = parseServerString(bodyText) ?: return@runBlocking null
                sessionManager.updateAccessToken(refreshed)
                refreshed
            } catch (e: Exception) {
                null
            }
        }

        if (newToken == null) {
            runBlocking { sessionManager.logout() }
            response
        } else {
            // Only discard the original response once we're actually
            // replacing it with the retry's.
            response.close()
            val retryRequest = request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
            chain.proceed(retryRequest)
        }
    }

fun createListsApi(serverUrl: String, accessToken: String, sessionManager: SessionManager): ListsApi {
    val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"

    val client = OkHttpClient.Builder()
        .addInterceptor(authRefreshInterceptor(serverUrl, accessToken, sessionManager))
        .build()

    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(eventGson))
        .build()
        .create(ListsApi::class.java)
}
