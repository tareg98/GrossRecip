package com.example.grossrecipes.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The backend's live-update channel: GET /Events/notify stays open
 * indefinitely and streams one line at a time - either ": heartbeat" (just a
 * keep-alive, means nothing) or "data: sync" (something changed somewhere -
 * go run a normal sync to find out what). This function just reads that
 * stream and emits once per real "data: sync" line; it doesn't know or care
 * what actually changed, since a normal sync (ListsRepository.syncPendingChanges)
 * already knows how to fetch and apply whatever's new.
 *
 * Reuses [authRefreshInterceptor] so an expired access token gets refreshed
 * the same way a normal sync call would - not that it helps much mid-stream
 * (an already-open connection doesn't get retried), but it does mean a fresh
 * *reconnect* attempt (see ListsRepository.listenForSyncSignals) picks up a
 * valid token instead of immediately failing with the same expired one.
 *
 * Emits nothing and simply closes if the connection drops, errors, or the
 * server ends the stream - reconnecting is the caller's job, not this
 * function's, since "how long to wait before trying again" isn't something
 * a single connection attempt should have an opinion about.
 */
fun observeSyncSignal(serverUrl: String, accessToken: String, sessionManager: SessionManager): Flow<Unit> = callbackFlow {
    val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"

    val client = OkHttpClient.Builder()
        // This connection is meant to stay open indefinitely - a normal read
        // timeout would kill it the moment the server goes quiet between
        // heartbeats, which is the entire point of it being quiet.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .addInterceptor(authRefreshInterceptor(serverUrl, accessToken, sessionManager))
        .build()

    val request = Request.Builder()
        .url("${baseUrl}Events/notify")
        .header("Accept", "text/event-stream")
        .build()

    val call = client.newCall(request)

    val job = launch(Dispatchers.IO) {
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    close(IOException("SSE connect failed: HTTP ${response.code}"))
                    return@use
                }
                val source = response.body?.source()
                if (source == null) {
                    close(IOException("SSE response had no body"))
                    return@use
                }
                while (isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    // Real payloads look like "data: sync" - a heartbeat is a
                    // bare ": heartbeat" comment line, which never matches
                    // the "data:" prefix and is silently skipped, exactly as
                    // it's meant to be (nothing to react to).
                    if (line.startsWith("data:") && line.removePrefix("data:").trim() == "sync") {
                        trySend(Unit)
                    }
                }
                close()
            }
        } catch (e: Exception) {
            close(e)
        }
    }

    awaitClose {
        call.cancel()
        job.cancel()
    }
}
