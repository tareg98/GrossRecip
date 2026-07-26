package com.example.grossrecipes.data

import com.example.grossrecipes.data.dto.PullEventsResponse
import com.example.grossrecipes.data.dto.PushEventsRequest
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Event-sourced sync: instead of one REST call per action (create list, set
 * checked, etc.), there are exactly two calls - push what happened here,
 * pull what happened everywhere else. This is a NEW backend contract (your
 * friend's PoC design) - the real backend doesn't have Events/push or
 * Events/pull yet. See ListsRepository for how events are built and applied.
 */
interface ListsApi {

    @POST("Events/push")
    suspend fun pushEvents(@Body request: PushEventsRequest): Response<ResponseBody>

    @GET("Events/pull")
    suspend fun pullEvents(
        @Query("since") since: Long,
        @Query("deviceId") deviceId: String
    ): Response<PullEventsResponse>
}

/**
 * [sessionManager] lets this client auto-recover from an expired access token:
 * on a 401/498, it calls the real /Account/refresh endpoint (using whatever
 * token is currently stored - refresh only works while that token is still
 * valid) and retries the failed request once with the new token. If refresh
 * itself fails, the session is cleared - the user has to log in again next
 * time they open the app.
 */
fun createListsApi(serverUrl: String, accessToken: String, sessionManager: SessionManager): ListsApi {
    val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"

    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val currentToken = runBlocking { sessionManager.currentSession().accessToken } ?: accessToken
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $currentToken")
                .build()
            chain.proceed(request)
        }
        .authenticator { _, response ->
            // Only try once - if a freshly-refreshed token still gets a 401,
            // something else is wrong and we shouldn't loop forever.
            if (response.priorResponse != null) return@authenticator null

            val oldToken = response.request.header("Authorization")?.removePrefix("Bearer ")
                ?: return@authenticator null

            val newToken = runBlocking {
                try {
                    val accountApi = createAccountApi(serverUrl, oldToken)
                    val refreshResponse = accountApi.refresh()
                    if (!refreshResponse.isSuccessful) return@runBlocking null
                    val bodyText = refreshResponse.body()?.string() ?: return@runBlocking null
                    val refreshed = Gson().fromJson(bodyText, String::class.java)
                    sessionManager.updateAccessToken(refreshed)
                    refreshed
                } catch (e: Exception) {
                    null
                }
            }

            if (newToken == null) {
                runBlocking { sessionManager.logout() }
                null
            } else {
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            }
        }
        .build()

    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ListsApi::class.java)
}
