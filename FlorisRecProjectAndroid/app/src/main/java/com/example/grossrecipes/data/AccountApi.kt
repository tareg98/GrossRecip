package com.example.grossrecipes.data

import com.example.grossrecipes.data.dto.Credentials
import com.example.grossrecipes.data.dto.LoginResponse
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface AccountApi {
    @POST("Account/login")
    suspend fun login(@Body credentials: Credentials): Response<ResponseBody>

    // Same request/response shape as login: send username+password, get back
    // a LoginResponse with tokens (so signing up logs you straight in), or
    // the plain string "Invalid credentials" if something's wrong (e.g. the
    // username's already taken).
    @POST("Account/register")
    suspend fun register(@Body credentials: Credentials): Response<ResponseBody>

    // Matches the real backend: refresh needs the CURRENT (not-yet-fully-expired)
    // access token in the Authorization header - it re-derives a new one from it.
    // No body needed.
    @POST("Account/refresh")
    suspend fun refresh(): Response<ResponseBody>
}

/** What happened when we tried to log in or sign up - both endpoints reply the same shape. */
sealed class AuthOutcome {
    data class Success(val accessToken: String, val refreshToken: String) : AuthOutcome()
    data class Failure(val message: String) : AuthOutcome()
}

/**
 * Login and sign-up both need to handle the exact same quirk: on failure the
 * backend replies with a plain string like "Invalid credentials" instead of
 * JSON. Gson parses that into a LoginResponse with every field null instead
 * of throwing, so the real check is "did we actually get an accessToken
 * back?" - this is the one place that knows that, used by both screens so
 * neither can drift out of sync with the other.
 */
suspend fun parseAuthResponse(response: Response<ResponseBody>): AuthOutcome {
    if (!response.isSuccessful) {
        return AuthOutcome.Failure("Server said: ${response.code()}")
    }
    val bodyText = response.body()?.string().orEmpty()
    return try {
        val parsed = Gson().fromJson(bodyText, LoginResponse::class.java)
        if (parsed?.accessToken == null) {
            AuthOutcome.Failure("Invalid username or password.")
        } else {
            AuthOutcome.Success(parsed.accessToken, parsed.refreshToken)
        }
    } catch (e: Exception) {
        AuthOutcome.Failure("Invalid username or password.")
    }
}

fun createAccountApi(serverUrl: String, accessToken: String? = null): AccountApi {
    val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"

    val clientBuilder = OkHttpClient.Builder()
    if (accessToken != null) {
        clientBuilder.addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            chain.proceed(request)
        }
    }

    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(clientBuilder.build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AccountApi::class.java)
}
