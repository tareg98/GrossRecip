package com.example.grossrecipes.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.room.withTransaction
import com.example.grossrecipes.data.local.AppDatabase
import com.example.grossrecipes.data.local.DividerDao
import com.example.grossrecipes.data.local.DividerEntity
import com.example.grossrecipes.data.local.KnownItemNameDao
import com.example.grossrecipes.data.local.KnownItemNameEntity
import com.example.grossrecipes.data.local.ListDao
import com.example.grossrecipes.data.local.ListEntity
import com.example.grossrecipes.data.local.ListItemDao
import com.example.grossrecipes.data.local.ListItemEntity
import com.example.grossrecipes.data.local.OutboxEventDao
import com.example.grossrecipes.data.local.OutboxEventEntity
import com.example.grossrecipes.ui.lists.GroceryItem
import com.example.grossrecipes.ui.lists.GroceryList
import com.sirolf2009.grossrecipes.event.Event
import com.sirolf2009.grossrecipes.event.list.ListCreated
import com.sirolf2009.grossrecipes.event.list.ListDeleted
import com.sirolf2009.grossrecipes.event.list.ListRecolored
import com.sirolf2009.grossrecipes.event.list.ListRenamed
import com.sirolf2009.grossrecipes.event.list.ListShared
import com.sirolf2009.grossrecipes.event.list.ListSharedExternally
import com.sirolf2009.grossrecipes.event.list.ListUnshared
import com.sirolf2009.grossrecipes.event.listItem.ListItemChecked
import com.sirolf2009.grossrecipes.event.listItem.ListItemCreated
import com.sirolf2009.grossrecipes.event.listItem.ListItemDeleted
import com.sirolf2009.grossrecipes.event.listItem.ListItemRenamed
import com.sirolf2009.grossrecipes.sync.dto.SyncRequest
import com.sirolf2009.grossrecipes.sync.dto.SyncResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Event-sourced, offline-first repository, now built directly on your
 * friend's gross-recipes-common library instead of our own generic event
 * shape - every user action becomes one of ITS typed [Event] subclasses
 * (ListCreated, ListItemChecked, etc.), which is (1) applied to the local
 * Room tables immediately, so the UI updates instantly with zero network,
 * and (2) queued in [OutboxEventDao] until the server confirms it. The local
 * tables ([ListEntity]/[ListItemEntity]) are just a "materialized view" -
 * the current snapshot you get by replaying every event applied so far.
 *
 * Sort order is deliberately NOT part of any event - see [updateSortOrder].
 *
 * Syncing ([syncPendingChanges]) is always the same shape regardless of why
 * it's running (first login, reconnect, or right after an action): send
 * whatever's in the outbox plus our last-synced cursor in one call, get back
 * everything we're missing, apply it, then forget the outbox (it's durable
 * on the server now). A brand-new install has a cursor of epoch, so its
 * first sync naturally receives the entire history and bootstraps its local
 * database - there's no separate "initial load" code path.
 *
 * [syncMutex] serializes every action + sync so only one ever touches the
 * outbox/cursor/local tables at a time. Without it, two overlapping syncs
 * (e.g. ListsViewModel's initial refresh() and its "just reconnected"
 * listener both firing at once on login/app-open) would both read the same
 * epoch cursor, both request the entire server history, and both apply that
 * history to Room concurrently and out of order relative to each other -
 * exactly what caused lists to flicker in and out on login, and part of why
 * "fully synced" could look true locally while a second, still-in-flight
 * request was still being processed server-side.
 *
 * Must be reached through [getInstance] rather than constructed directly -
 * both ListsViewModel and SettingsViewModel need to see the SAME [syncMutex]
 * and [isSyncing] to have any meaning across screens; two separate instances
 * would each have their own, defeating the point of either.
 */
class ListsRepository(
    private val database: AppDatabase,
    private val listDao: ListDao,
    private val listItemDao: ListItemDao,
    private val outboxEventDao: OutboxEventDao,
    private val dividerDao: DividerDao,
    private val knownItemNameDao: KnownItemNameDao,
    private val syncStateManager: SyncStateManager,
    private val sessionManager: SessionManager
) {

    private val syncMutex = Mutex()

    // True for exactly as long as an actual network sync is in flight - the
    // one thing neither connectivity nor the outbox count can tell you.
    // Right after login the outbox is legitimately empty (nothing local has
    // been created yet to push), so "pending changes == 0" alone looks
    // "synced" even though the very first pull from the server hasn't
    // happened yet - this flag is what covers that gap.
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // The other gap pendingChangeCount alone can't cover: a PULL can fail
    // (e.g. a rejected token, a server error) while the outbox is already
    // empty, because there was nothing local to push in the first place -
    // that leaves pendingChangeCount == 0 and isSyncing back to false right
    // after the failure, which looked identical to "fully synced" before
    // this existed. Cleared only by the next sync that actually succeeds.
    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private fun api(serverUrl: String, accessToken: String) =
        createListsApi(serverUrl, accessToken, sessionManager)

    fun observeLists(): Flow<List<GroceryList>> =
        combine(listDao.observeLists(), listItemDao.observeAllItems(), dividerDao.observeAll()) { lists, items, dividers ->
            lists.map { list ->
                val listDividers = dividers.filter { it.listId == list.id }
                list.toDomain(
                    items = items.filter { it.listId == list.id },
                    dividerAtGapIndices = listDividers.map { it.gapIndex }.toSet()
                )
            }
        }

    // A permanent history, not just names of items that still exist - see
    // KnownItemNameEntity's doc for why: deleting an item used to make its
    // name stop suggesting itself entirely, which isn't what "autocomplete
    // from what I've typed before" should mean.
    fun observeKnownItemNames(): Flow<List<String>> =
        knownItemNameDao.observeAll().map { names -> names.map { it.displayName } }

    // A row only ever leaves the outbox once a sync actually round-trips
    // successfully - so together with isSyncing, this is what "fully synced"
    // actually means: not mid-sync, and nothing left waiting to go out.
    fun observePendingChangeCount(): Flow<Int> = outboxEventDao.observePendingCount()

    // ---- Actions: each one builds an event, applies it locally right away, ----
    // ---- queues it, then tries to sync immediately in case we're online.   ----
    // ---- The whole thing runs under syncMutex so it can never interleave   ----
    // ---- with another action or a background sync.                        ----

    suspend fun createList(
        serverUrl: String,
        accessToken: String,
        name: String,
        color: Color?,
        sharedWithUserId: String
    ): Result<Unit> = syncMutex.withLock {
        val owner = sessionManager.currentSession().username

        applyAndQueue(
            ListCreated(
                time = ZonedDateTime.now(),
                listId = UUID.randomUUID(),
                name = name,
                owner = owner,
                sharedWith = if (sharedWithUserId.isNotBlank()) listOf(sharedWithUserId) else emptyList(),
                color = color?.toHex()
            )
        )

        syncPendingChangesLocked(serverUrl, accessToken)
    }

    suspend fun addItem(serverUrl: String, accessToken: String, listId: String, itemName: String): Result<Unit> =
        syncMutex.withLock {
            val trimmedName = itemName.trim()
            // Same name-matching rule already used to merge duplicate adds
            // from two devices (reconcileDuplicateItems) - case/spacing
            // insensitive. A match that's already checked off gets
            // unchecked instead of creating a second copy of the same item;
            // a match that's already sitting unchecked is left alone as-is -
            // either way, typing a name that's already on the list doesn't
            // produce a duplicate.
            val existing = listItemDao.getForList(listId)
                .firstOrNull { it.name.trim().equals(trimmedName, ignoreCase = true) }

            when {
                existing == null -> applyAndQueue(
                    ListItemCreated(ZonedDateTime.now(), UUID.fromString(listId), UUID.randomUUID(), trimmedName)
                )
                existing.checked -> applyAndQueue(
                    ListItemChecked(ZonedDateTime.now(), UUID.fromString(listId), UUID.fromString(existing.id), false)
                )
                else -> { /* already there, unchecked - nothing to do */ }
            }

            syncPendingChangesLocked(serverUrl, accessToken)
        }

    suspend fun setChecked(serverUrl: String, accessToken: String, itemId: String, checked: Boolean): Result<Unit> =
        syncMutex.withLock {
            val item = listItemDao.getById(itemId)
                ?: return@withLock Result.failure(Exception("Item not found locally: $itemId"))
            applyAndQueue(ListItemChecked(ZonedDateTime.now(), UUID.fromString(item.listId), UUID.fromString(itemId), checked))
            syncPendingChangesLocked(serverUrl, accessToken)
        }

    suspend fun deleteList(serverUrl: String, accessToken: String, listId: String): Result<Unit> =
        syncMutex.withLock {
            // Only the owner's delete should remove the list for everyone. Anyone
            // it's just shared with "deleting" it should only remove it from
            // their own view - modeled as them unsharing themselves, not as a
            // real ListDeleted, so the owner and everyone else keep their copy.
            val session = sessionManager.currentSession()
            val list = listDao.getById(listId)
            val event: Event = if (list != null && list.owner == session.username) {
                ListDeleted(ZonedDateTime.now(), UUID.fromString(listId))
            } else {
                // ListUnshared carries a userId (see shareList below), not a
                // username - session.userId, not session.username.
                ListUnshared(ZonedDateTime.now(), UUID.fromString(listId), session.userId)
            }
            applyAndQueue(event)
            syncPendingChangesLocked(serverUrl, accessToken)
        }

    suspend fun deleteItem(serverUrl: String, accessToken: String, itemId: String): Result<Unit> =
        syncMutex.withLock {
            val item = listItemDao.getById(itemId)
                ?: return@withLock Result.failure(Exception("Item not found locally: $itemId"))
            applyAndQueue(ListItemDeleted(ZonedDateTime.now(), UUID.fromString(item.listId), UUID.fromString(itemId)))
            syncPendingChangesLocked(serverUrl, accessToken)
        }

    suspend fun setColor(serverUrl: String, accessToken: String, listId: String, color: Color?): Result<Unit> =
        syncMutex.withLock {
            applyAndQueue(ListRecolored(ZonedDateTime.now(), UUID.fromString(listId), color?.toHex()))
            syncPendingChangesLocked(serverUrl, accessToken)
        }

    // Both of these take the recipient's UUID, not their username - resolved
    // via lookupUsername in ShareDialog before either of these gets called,
    // so a share/unshare always identifies "who" the same durable way an
    // incoming ListShared/ListUnshared from the server does, regardless of
    // whether that person's username ever changes later.
    suspend fun shareList(serverUrl: String, accessToken: String, listId: String, userId: String): Result<Unit> =
        syncMutex.withLock {
            applyAndQueue(ListShared(ZonedDateTime.now(), UUID.fromString(listId), userId))
            syncPendingChangesLocked(serverUrl, accessToken)
        }

    suspend fun unshareList(serverUrl: String, accessToken: String, listId: String, userId: String): Result<Unit> =
        syncMutex.withLock {
            applyAndQueue(ListUnshared(ZonedDateTime.now(), UUID.fromString(listId), userId))
            syncPendingChangesLocked(serverUrl, accessToken)
        }

    suspend fun markSharedExternally(serverUrl: String, accessToken: String, listId: String): Result<Unit> =
        syncMutex.withLock {
            applyAndQueue(ListSharedExternally(ZonedDateTime.now(), UUID.fromString(listId)))
            syncPendingChangesLocked(serverUrl, accessToken)
        }

    /**
     * Purely local, like [setCheckedSectionExpandedLocalOnly] - never an event, never synced.
     * gross-recipes-common has no concept of list ordering at all, and for
     * good reason: "position 2" only means something relative to the set of
     * lists YOU can see, so broadcasting it as an event would apply your
     * ordering to everyone a shared list is visible to. Each device keeps
     * its own ordering.
     */
    suspend fun updateSortOrder(orderedListIds: List<String>) {
        orderedListIds.forEachIndexed { index, id ->
            val existing = listDao.getById(id)
            if (existing != null && existing.sortOrder != index) {
                listDao.upsert(existing.copy(sortOrder = index))
            }
        }
    }

    /** Same reasoning as [updateSortOrder], one level down - which item comes first within a single list. */
    suspend fun updateItemSortOrder(orderedItemIds: List<String>) {
        orderedItemIds.forEachIndexed { index, id ->
            val existing = listItemDao.getById(id)
            if (existing != null && existing.sortOrder != index) {
                listItemDao.upsert(existing.copy(sortOrder = index))
            }
        }
    }

    /** Pure UI state (which list's checked-off section is expanded) - never an event, never synced. */
    suspend fun setCheckedSectionExpandedLocalOnly(listId: String, expanded: Boolean) {
        listDao.getById(listId)?.let { listDao.upsert(it.copy(checkedSectionExpanded = expanded)) }
    }

    /**
     * Purely local, like [setCheckedSectionExpandedLocalOnly] and
     * [updateSortOrder] - a divider is a personal organizational aid, not
     * something gross-recipes-common has any concept of, so it never becomes
     * an event and never syncs. [gapIndex] is a raw position (0 = above the
     * first item, 1 = between the 1st and 2nd, etc.) - see DividerEntity's
     * doc for why this is anchored to a position rather than an item.
     */
    suspend fun toggleDivider(listId: String, gapIndex: Int) {
        val existing = dividerDao.getAt(listId, gapIndex)
        if (existing != null) {
            dividerDao.deleteAt(listId, gapIndex)
        } else {
            dividerDao.upsert(DividerEntity(id = UUID.randomUUID().toString(), listId = listId, gapIndex = gapIndex))
        }
    }

    /** Called on logout so a different account logging in on this phone doesn't see stale data. */
    suspend fun clearAllLocalData() = syncMutex.withLock {
        listDao.deleteAll()
        listItemDao.deleteAll()
        dividerDao.deleteAll()
        knownItemNameDao.deleteAll()
        outboxEventDao.deleteAll()
        syncStateManager.reset()
    }

    // ---- The sync cycle: send our outbox + cursor, apply what comes back. ----

    /** Called directly (not via an action) on startup, on reconnect, and from the manual refresh button. */
    suspend fun syncPendingChanges(serverUrl: String, accessToken: String): Result<Unit> =
        syncMutex.withLock { syncPendingChangesLocked(serverUrl, accessToken) }

    /**
     * Keeps a live connection open to the server's Events/notify SSE stream
     * (see [observeSyncSignal]) and runs a normal sync every time it reports
     * something changed - catches another device's edits within moments
     * instead of only the next time this one happens to reconnect or the app
     * gets reopened. Runs until the calling coroutine is cancelled (see
     * ListsViewModel, which ties this to "online and logged in").
     *
     * A dropped connection, a server error, or the stream simply ending all
     * look the same from here: [observeSyncSignal]'s Flow just completes.
     * Rather than give up on live sync entirely, this waits a few seconds
     * and reconnects - short enough to feel responsive again quickly, long
     * enough that a server hiccup doesn't spin this in a tight reconnect loop.
     */
    suspend fun listenForSyncSignals(serverUrl: String, accessToken: String) {
        while (currentCoroutineContext().isActive) {
            try {
                observeSyncSignal(serverUrl, accessToken, sessionManager).collect {
                    syncMutex.withLock { syncPendingChangesLocked(serverUrl, accessToken) }
                }
            } catch (e: Exception) {
                // Connection dropped or failed - fall through to the delay
                // below and try again rather than staying disconnected.
            }
            delay(5000)
        }
    }

    /** Same as [syncPendingChanges] but assumes the caller already holds [syncMutex] - never call this directly. */
    private suspend fun syncPendingChangesLocked(serverUrl: String, accessToken: String): Result<Unit> {
        _isSyncing.value = true
        try {
            val result = syncPendingChangesInner(serverUrl, accessToken)
            // Cleared only on an actual success - a failed attempt (even one
            // with nothing local to push, so pendingChangeCount stays 0
            // throughout) leaves this set so the status doesn't silently
            // revert to "Synced" the moment isSyncing goes back to false.
            result.onSuccess { _lastSyncError.value = null }
            result.onFailure { e -> _lastSyncError.value = e.message ?: "Sync failed" }
            return result
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Self-heals a session that doesn't know its own userId yet - either an
     * existing login from before ListShared/ListUnshared switched to
     * carrying a userId instead of a username (see shareList), or a login
     * where the one-time self-lookup happened to fail right after signing
     * in. Runs at the top of every sync (cheap no-op once resolved, since
     * it checks the stored value first) rather than only at login, so an
     * old session heals itself the next time it's online instead of staying
     * broken until the user thinks to log out and back in.
     */
    private suspend fun ensureMyUserIdKnown() {
        val session = sessionManager.currentSession()
        val accessToken = session.accessToken
        if (session.userId.isNotBlank() || session.username.isBlank() || accessToken == null) return
        runCatching {
            lookupUsername(session.serverUrl, accessToken, session.username).getOrNull()
        }.getOrNull()?.let { resolvedId ->
            sessionManager.saveUserId(resolvedId)
        }
    }

    private suspend fun syncPendingChangesInner(serverUrl: String, accessToken: String): Result<Unit> {
        return try {
            ensureMyUserIdKnown()
            val client = api(serverUrl, accessToken)

            val outbox = outboxEventDao.getAll()
            val lastSync = syncStateManager.getLastSync()

            val response = client.sync(SyncRequest(outbox.map { it.event }, lastSync))
            if (!response.isSuccessful) {
                // Surface whatever the server actually said (e.g. the real
                // MongoDB auth error text) instead of just a bare status code,
                // so a failure has an actual reason attached to it.
                val detail = runCatching { response.errorBody()?.string() }.getOrNull()?.take(300)
                val message = "Sync failed: HTTP ${response.code()}" + if (!detail.isNullOrBlank()) " - $detail" else ""
                return Result.failure(Exception(message))
            }
            val body: SyncResponse = response.body() ?: return Result.failure(Exception("Sync failed: empty response from server"))

            // No client-side relevance filtering on what the server sends
            // back - deciding who's allowed to see what is the backend's job,
            // not something the client should be quietly re-checking (and
            // possibly masking a real leak behind, if the server ever gets it
            // wrong - filtering it out here would just hide that from
            // testing instead of surfacing it). Whatever comes back gets applied.
            val receivedEvents = body.serverEvents.flatMap { patch -> patch.events }

            // Everything from here down is one Room transaction - without
            // this, observeLists() (backed by Room's own Flow) re-emits after
            // EVERY individual upsert/delete as events get applied one at a
            // time, so a big catch-up sync (first login, or reconnecting
            // after a while) visibly flickered lists in and out as it worked
            // through the list. Wrapping it means the UI only ever sees the
            // state before the sync and the state after - nothing in between.
            database.withTransaction {
                receivedEvents.forEach { event ->
                    // Assumes events arrive in the order they actually happened
                    // (so e.g. a ListItemCreated never arrives before its list's
                    // ListCreated) - if that's ever violated, skip just the one
                    // bad event instead of aborting the whole sync.
                    runCatching { applyEvent(event) }
                }

                // Your friend's merge algorithm: if someone else already added the
                // same item (same list, name matching ignoring case/spacing)
                // while we were both offline, we only find out now - too late to
                // stop our own copy from having just been sent above. So instead
                // we fold our copy into theirs: carry over any local state (like
                // already being checked) onto their item, then drop ours, so only
                // one row shows up on screen from here on.
                reconcileDuplicateItems(outbox.map { it.event }, receivedEvents)

                // Everything above was just sent to the server as part of this
                // same request - durable now, so this phone doesn't need to
                // remember any of it (including anything just merged away).
                outbox.forEach { outboxEventDao.deleteBySeq(it.seq) }
            }

            // Server-stamped cursor (added in 0.6) - same reasoning as our old
            // backend's serverTime: never trust a client's own clock for this.
            // DataStore, not Room, so it's outside the transaction above.
            syncStateManager.setLastSync(body.syncDate)

            Result.success(Unit)
        } catch (e: Exception) {
            // Nothing local is touched above this point unless the sync
            // actually succeeded, so a failed sync never loses data - it just
            // leaves the outbox queued to retry next time. This message is
            // what the user actually sees, so it needs to say why.
            Result.failure(Exception("Sync failed: ${e.message ?: e.javaClass.simpleName}", e))
        }
    }

    /**
     * Builds an event, applies it locally right now, and queues it for the
     * next sync. Assumes the caller already holds [syncMutex] - never call
     * this directly.
     */
    private suspend fun applyAndQueue(event: Event) {
        database.withTransaction {
            applyEvent(event)
            outboxEventDao.insert(OutboxEventEntity(event = event))
        }
    }

    private suspend fun reconcileDuplicateItems(pushedEvents: List<Event>, receivedEvents: List<Event>) {
        val ourAdds = pushedEvents.filterIsInstance<ListItemCreated>()
        val theirAdds = receivedEvents.filterIsInstance<ListItemCreated>()

        for (ours in ourAdds) {
            val match = theirAdds.firstOrNull { theirs ->
                theirs.listId == ours.listId &&
                    theirs.name.trim().equals(ours.name.trim(), ignoreCase = true)
            } ?: continue

            val ourItemId = ours.id.toString()
            val theirItemId = match.id.toString()
            if (ourItemId == theirItemId) continue

            // Their item was already created (via applyEvent above) - carry
            // over anything we'd already done locally to our copy before we drop it.
            val ourRow = listItemDao.getById(ourItemId)
            if (ourRow?.checked == true) {
                listItemDao.getById(theirItemId)?.let { theirRow ->
                    listItemDao.upsert(theirRow.copy(checked = true))
                }
            }
            listItemDao.deleteById(ourItemId)
        }
    }

    /**
     * The reducer: turns one event into a local database change. This is the
     * one place that knows what each event type means - used both for events
     * we just created ourselves and events received from other devices, so
     * applying an event always has the exact same effect no matter which
     * phone originally produced it.
     */
    private suspend fun applyEvent(event: Event) {
        when (event) {
            is ListCreated -> {
                // No ownership/sharedWith check here - the backend decides
                // who's allowed to receive which events, not the client.
                // (There used to be a check here that only looked at this
                // one event's own sharedWith snapshot, which is empty at
                // creation time - that's what silently dropped lists shared
                // with me later, since a following ListShared for the same
                // list had nothing locally to attach to.)
                val id = event.listId.toString()
                val existing = listDao.getById(id)
                listDao.upsert(
                    ListEntity(
                        id = id,
                        name = event.name,
                        owner = event.owner,
                        colorHex = event.color?.ifBlank { null } ?: existing?.colorHex,
                        sharedWith = if (event.sharedWith.isNotEmpty()) event.sharedWith else existing?.sharedWith ?: emptyList(),
                        sharedExternally = existing?.sharedExternally ?: false,
                        sortOrder = existing?.sortOrder ?: (listDao.maxSortOrder() + 1),
                        checkedSectionExpanded = existing?.checkedSectionExpanded ?: false
                    )
                )
            }
            is ListRenamed -> {
                listDao.getById(event.listId.toString())?.let { existing ->
                    listDao.upsert(existing.copy(name = event.name))
                }
            }
            is ListDeleted -> listDao.deleteById(event.listId.toString())
            is ListRecolored -> {
                listDao.getById(event.listId.toString())?.let { existing ->
                    listDao.upsert(existing.copy(colorHex = event.color?.ifBlank { null }))
                }
            }
            is ListShared -> {
                listDao.getById(event.listId.toString())?.let { existing ->
                    if (event.sharedWith !in existing.sharedWith) {
                        listDao.upsert(existing.copy(sharedWith = existing.sharedWith + event.sharedWith))
                    }
                }
            }
            is ListUnshared -> {
                // event.unsharedWith is a userId now (see shareList/
                // unshareList above), not a username.
                val me = sessionManager.currentSession().userId
                if (event.unsharedWith == me) {
                    // I'm the one being removed - this list is no longer mine
                    // to see at all (this is also how a non-owner's "delete"
                    // is modeled - see deleteList). FK cascade removes its
                    // items along with the row via the real @Upsert-safe delete.
                    listDao.deleteById(event.listId.toString())
                } else {
                    listDao.getById(event.listId.toString())?.let { existing ->
                        listDao.upsert(existing.copy(sharedWith = existing.sharedWith - event.unsharedWith))
                    }
                }
            }
            is ListSharedExternally -> {
                listDao.getById(event.listId.toString())?.let { existing ->
                    listDao.upsert(existing.copy(sharedExternally = true))
                }
            }
            is ListItemCreated -> {
                // Only if we actually have the parent list - if its
                // ListCreated hasn't arrived yet (events out of order) or the
                // list was since deleted locally, don't stash an orphaned item.
                val listId = event.listId.toString()
                if (listDao.getById(listId) != null) {
                    listItemDao.upsert(
                        ListItemEntity(
                            id = event.id.toString(),
                            listId = listId,
                            name = event.name,
                            checked = false,
                            // Appends at the end by default, like a new list
                            // itself does (ListDao.maxSortOrder) - a synced
                            // item from another device lands wherever it
                            // would visually make sense, at the bottom, not
                            // wherever this device's drag-reordering (purely
                            // local) happens to have things arranged.
                            sortOrder = listItemDao.maxSortOrder(listId) + 1
                        )
                    )
                    // Recorded regardless of which device created this item -
                    // autocomplete suggestions are shared across every list,
                    // and this is the one place every item creation (local
                    // or synced) passes through.
                    knownItemNameDao.insertIfAbsent(
                        KnownItemNameEntity(normalizedName = event.name.trim().lowercase(), displayName = event.name.trim())
                    )
                }
            }
            is ListItemRenamed -> {
                listItemDao.getById(event.listItemId.toString())?.let { existing ->
                    listItemDao.upsert(existing.copy(name = event.name))
                }
            }
            is ListItemChecked -> {
                listItemDao.getById(event.listItemId.toString())?.let { existing ->
                    listItemDao.upsert(existing.copy(checked = event.checked))
                }
            }
            is ListItemDeleted -> {
                // Dividers are position-anchored now (see DividerEntity), not
                // tied to any item's id, so deleting an item needs no divider
                // cleanup here - a divider at, say, gap 2 just keeps meaning
                // "gap 2", whatever ends up on either side of it.
                listItemDao.deleteById(event.listItemId.toString())
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ListsRepository? = null

        /** Same one instance app-wide - see the class doc for why that matters (shared syncMutex/isSyncing). */
        fun getInstance(context: Context): ListsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val database = AppDatabase.getInstance(appContext)
                    ListsRepository(
                        database,
                        database.listDao(),
                        database.listItemDao(),
                        database.outboxEventDao(),
                        database.dividerDao(),
                        database.knownItemNameDao(),
                        SyncStateManager(appContext),
                        SessionManager(appContext)
                    ).also { INSTANCE = it }
                }
            }
        }
    }
}

private fun ListEntity.toDomain(
    items: List<ListItemEntity>,
    dividerAtGapIndices: Set<Int>
): GroceryList = GroceryList(
    id = id,
    name = name,
    color = colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() },
    sharedWith = sharedWith,
    sharedExternally = sharedExternally,
    items = items.map { GroceryItem(id = it.id, name = it.name, checked = it.checked) },
    checkedSectionExpanded = checkedSectionExpanded,
    dividerAtGapIndices = dividerAtGapIndices
)

private fun Color.toHex(): String = String.format("#%08X", this.toArgb())
