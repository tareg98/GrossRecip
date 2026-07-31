package com.example.grossrecipes.data

import com.example.grossrecipes.data.dto.Credentials
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AccountApi {
    @POST("Account/login")
    suspend fun login(@Body credentials: Credentials): Response<ResponseBody>

    // Same request/response shape as login: send username+password, get back
    // a LoginResponse with tokens (so signing up logs you straight in), or
    // the plain string "Invalid credentials" if something's wrong (e.g. the
    // username's already taken).
    @POST("Account/register")
    suspend fun register(@Body credentials: Credentials): Response<ResponseBody>

    // Takes the REFRESH token in the Authorization header (not the access
    // token) - that's the one meant to still be valid long after the access
    // token itself has expired, which is the entire point of refreshing.
    // See ListsApi.createListsApi's Authenticator for where this gets called.
    // No body needed.
    @POST("Account/refresh")
    suspend fun refresh(): Response<ResponseBody>

    // PLACEHOLDER - path/method not confirmed with the backend yet. Meant to
    // resolve a typed username to that user's UUID before sharing, replying
    // either with the UUID or some "not found" shape. Whatever the real
    // contract turns out to be, only this one declaration (and the response
    // parsing in lookupUsername below) should need to change.
    @GET("Account/lookup/{username}")
    suspend fun lookupUsername(@Path("username") username: String): Response<ResponseBody>
}

/** What happened when we tried to log in or sign up - both endpoints reply the same shape. */
sealed class AuthOutcome {
    data class Success(val accessToken: String, val refreshToken: String) : AuthOutcome()
    data class Failure(val message: String) : AuthOutcome()
}

/**
 * Login and sign-up both need to handle two quirks in how this backend
 * replies:
 *
 * 1. On an actual rejection, it replies with a plain string (e.g. "Invalid
 *    credentials") instead of JSON, over an HTTP 200 - not a 4xx - so the
 *    real check is "did we actually get an accessToken back?", not the
 *    status code.
 * 2. On success, at least register (maybe login too, maybe not - unclear,
 *    so both are handled the same way defensively) wraps the real payload in
 *    an extra {"value": {...}} envelope instead of returning accessToken/
 *    refreshToken at the top level. Found by actually looking at the raw
 *    body instead of trusting the old hardcoded failure message, which is
 *    what made a genuinely successful registration look like a rejected one
 *    ("Invalid username or password") - the tokens were right there in the
 *    response the whole time, just one level deeper than expected.
 *
 * This is the one place that knows both of those, used by both screens so
 * neither can drift out of sync with the other.
 */
suspend fun parseAuthResponse(response: Response<ResponseBody>): AuthOutcome {
    if (!response.isSuccessful) {
        val detail = runCatching { response.errorBody()?.string() }.getOrNull()?.trim()?.take(300)
        val message = "Server said: HTTP ${response.code()}" + if (!detail.isNullOrBlank()) " - $detail" else ""
        return AuthOutcome.Failure(message)
    }
    val bodyText = response.body()?.string().orEmpty()
    return try {
        val root = JsonParser.parseString(bodyText).asJsonObject
        // Unwrap the {"value": {...}} envelope if it's there; otherwise
        // assume the tokens are already at the top level.
        val payload = if (root.has("value") && root.get("value").isJsonObject) {
            root.getAsJsonObject("value")
        } else {
            root
        }
        val accessToken = payload.get("accessToken")?.takeIf { !it.isJsonNull }?.asString
        val refreshToken = payload.get("refreshToken")?.takeIf { !it.isJsonNull }?.asString
        if (accessToken != null && refreshToken != null) {
            AuthOutcome.Success(accessToken, refreshToken)
        } else {
            val detail = bodyText.trim().removeSurrounding("\"").ifBlank { null }
            AuthOutcome.Failure(detail ?: "Invalid username or password.")
        }
    } catch (e: Exception) {
        // Not a JSON object at all - the plain-string rejection case.
        AuthOutcome.Failure(bodyText.trim().removeSurrounding("\"").ifBlank { "Invalid username or password." })
    }
}

/**
 * Parses a plain-string server response that might come back as a raw JSON
 * string ("...") or wrapped in the same {"value": "..."} envelope seen on
 * login/register (see [parseAuthResponse]'s doc) - used for /Account/refresh,
 * which just returns the new access token as a bare string. If refresh's
 * response turns out to be wrapped the same way login/register's is, the old
 * `Gson().fromJson(bodyText, String::class.java)` here would throw trying to
 * deserialize a JSON object as a plain String, get swallowed by the caller's
 * catch block, and look exactly like a failed refresh - forcing a real login
 * even though the server actually sent back a perfectly good new token.
 */
fun parseServerString(bodyText: String): String? {
    return try {
        val element = JsonParser.parseString(bodyText)
        when {
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
            element.isJsonObject -> {
                val value = element.asJsonObject.get("value")
                if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
            }
            else -> null
        }
    } catch (e: Exception) {
        null
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

/**
 * Resolves a typed username to that user's UUID before sharing with them,
 * instead of trusting whatever was typed and only finding out it's wrong
 * once the share event fails to apply on someone else's device. Returns
 * `Result.success(uuid)` if found, `Result.success(null)` if the username
 * doesn't exist, `Result.failure` only for an actual network/server problem.
 *
 * The exact "not found" shape isn't confirmed with the backend yet, so this
 * defensively covers the two most likely conventions - a 404, or a 200 with
 * an empty/blank/"null" body - until we hear back. Once the real contract is
 * known, only this function's body should need updating.
 */
suspend fun lookupUsername(serverUrl: String, accessToken: String? = null, username: String): Result<String?> {
    return try {
        val api = createAccountApi(serverUrl, accessToken)
        val response = api.lookupUsername(username)
        when {
            response.code() == 404 -> Result.success(null)
            response.isSuccessful -> {
                val bodyText = response.body()?.string()?.trim().orEmpty()
                if (bodyText.isBlank() || bodyText.equals("null", ignoreCase = true)) {
                    Result.success(null)
                } else {
                    // Body might come back as a raw UUID or a JSON-quoted
                    // string ("\"...\"") depending on how the endpoint
                    // serializes a plain string - strip quotes either way.
                    Result.success(bodyText.removeSurrounding("\""))
                }
            }
            else -> Result.failure(Exception("Lookup failed: HTTP ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Lookup failed: ${e.message ?: e.javaClass.simpleName}"))
    }
}
