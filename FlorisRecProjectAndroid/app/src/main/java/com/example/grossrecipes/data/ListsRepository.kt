package com.example.grossrecipes.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
 */
class ListsRepository(
    private val listDao: ListDao,
    private val listItemDao: ListItemDao,
    private val outboxEventDao: OutboxEventDao,
    private val syncStateManager: SyncStateManager,
    private val sessionManager: SessionManager
) {

    private fun api(serverUrl: String, accessToken: String) =
        createListsApi(serverUrl, accessToken, sessionManager)

    fun observeLists(): Flow<List<GroceryList>> =
        combine(listDao.observeLists(), listItemDao.observeAllItems()) { lists, items ->
            lists.map { list -> list.toDomain(items.filter { it.listId == list.id }) }
        }

    fun observeKnownItemNames(): Flow<List<String>> =
        listItemDao.observeAllItems().map { items -> items.map { it.name }.distinct() }

    // ---- Actions: each one builds an event, applies it locally right away, ----
    // ---- queues it, then tries to sync immediately in case we're online.   ----

    suspend fun createList(
        serverUrl: String,
        accessToken: String,
        name: String,
        color: Color?,
        sharedWithUsername: String
    ): Result<Unit> {
        val owner = sessionManager.currentSession().username

        emit(
            ListCreated(
                time = ZonedDateTime.now(),
                listId = UUID.randomUUID(),
                name = name,
                owner = owner,
                sharedWith = if (sharedWithUsername.isNotBlank()) listOf(sharedWithUsername) else emptyList(),
                color = color?.toHex()
            )
        )

        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun addItem(serverUrl: String, accessToken: String, listId: String, itemName: String): Result<Unit> {
        emit(ListItemCreated(ZonedDateTime.now(), UUID.fromString(listId), UUID.randomUUID(), itemName))
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun setChecked(serverUrl: String, accessToken: String, itemId: String, checked: Boolean): Result<Unit> {
        val item = listItemDao.getById(itemId) ?: return Result.failure(Exception("Item not found locally: $itemId"))
        emit(ListItemChecked(ZonedDateTime.now(), UUID.fromString(item.listId), UUID.fromString(itemId), checked))
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun deleteList(serverUrl: String, accessToken: String, listId: String): Result<Unit> {
        emit(ListDeleted(ZonedDateTime.now(), UUID.fromString(listId)))
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun deleteItem(serverUrl: String, accessToken: String, itemId: String): Result<Unit> {
        val item = listItemDao.getById(itemId) ?: return Result.failure(Exception("Item not found locally: $itemId"))
        emit(ListItemDeleted(ZonedDateTime.now(), UUID.fromString(item.listId), UUID.fromString(itemId)))
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun setColor(serverUrl: String, accessToken: String, listId: String, color: Color?): Result<Unit> {
        emit(ListRecolored(ZonedDateTime.now(), UUID.fromString(listId), color?.toHex()))
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun shareList(serverUrl: String, accessToken: String, listId: String, username: String): Result<Unit> {
        emit(ListShared(ZonedDateTime.now(), UUID.fromString(listId), username))
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun unshareList(serverUrl: String, accessToken: String, listId: String, username: String): Result<Unit> {
        emit(ListUnshared(ZonedDateTime.now(), UUID.fromString(listId), username))
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun markSharedExternally(serverUrl: String, accessToken: String, listId: String): Result<Unit> {
        emit(ListSharedExternally(ZonedDateTime.now(), UUID.fromString(listId)))
        return syncPendingChanges(serverUrl, accessToken)
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

    /** Pure UI state (which list's checked-off section is expanded) - never an event, never synced. */
    suspend fun setCheckedSectionExpandedLocalOnly(listId: String, expanded: Boolean) {
        listDao.getById(listId)?.let { listDao.upsert(it.copy(checkedSectionExpanded = expanded)) }
    }

    /** Called on logout so a different account logging in on this phone doesn't see stale data. */
    suspend fun clearAllLocalData() {
        listDao.deleteAll()
        listItemDao.deleteAll()
        outboxEventDao.deleteAll()
        syncStateManager.reset()
    }

    // ---- The sync cycle: send our outbox + cursor, apply what comes back. ----

    suspend fun syncPendingChanges(serverUrl: String, accessToken: String): Result<Unit> {
        return try {
            val client = api(serverUrl, accessToken)

            val outbox = outboxEventDao.getAll()
            val lastSync = syncStateManager.getLastSync()

            val response = client.sync(SyncRequest(outbox.map { it.event }, lastSync))
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code()}"))
            }
            val body: SyncResponse = response.body() ?: return Result.failure(Exception("Empty response"))

            // SyncResponse groups events into Patches per list - flatten back
            // into one ordered stream to apply, same as before.
            val receivedEvents = body.serverEvents.flatMap { patch -> patch.events }
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

            // Server-stamped cursor (added in 0.6) - same reasoning as our old
            // backend's serverTime: never trust a client's own clock for this.
            syncStateManager.setLastSync(body.syncDate)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Builds an event, applies it locally right now, and queues it for the next sync. */
    private suspend fun emit(event: Event) {
        applyEvent(event)
        outboxEventDao.insert(OutboxEventEntity(event = event))
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
                listDao.getById(event.listId.toString())?.let { existing ->
                    listDao.upsert(existing.copy(sharedWith = existing.sharedWith - event.unsharedWith))
                }
            }
            is ListSharedExternally -> {
                listDao.getById(event.listId.toString())?.let { existing ->
                    listDao.upsert(existing.copy(sharedExternally = true))
                }
            }
            is ListItemCreated -> {
                listItemDao.upsert(
                    ListItemEntity(
                        id = event.id.toString(),
                        listId = event.listId.toString(),
                        name = event.name,
                        checked = false
                    )
                )
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
            is ListItemDeleted -> listItemDao.deleteById(event.listItemId.toString())
        }
    }
}

private fun ListEntity.toDomain(items: List<ListItemEntity>): GroceryList = GroceryList(
    id = id,
    name = name,
    color = colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() },
    sharedWith = sharedWith,
    sharedExternally = sharedExternally,
    items = items.map { GroceryItem(id = it.id, name = it.name, checked = it.checked) },
    checkedSectionExpanded = checkedSectionExpanded
)

private fun Color.toHex(): String = String.format("#%08X", this.toArgb())
