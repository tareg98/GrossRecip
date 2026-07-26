package com.example.grossrecipes.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.grossrecipes.data.dto.EventEnvelope
import com.example.grossrecipes.data.dto.EventType
import com.example.grossrecipes.data.dto.PullEventsResponse
import com.example.grossrecipes.data.dto.PushEventsRequest
import com.example.grossrecipes.data.local.ListDao
import com.example.grossrecipes.data.local.ListEntity
import com.example.grossrecipes.data.local.ListItemDao
import com.example.grossrecipes.data.local.ListItemEntity
import com.example.grossrecipes.data.local.OutboxEventDao
import com.example.grossrecipes.data.local.OutboxEventEntity
import com.example.grossrecipes.ui.lists.GroceryItem
import com.example.grossrecipes.ui.lists.GroceryList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Event-sourced, offline-first repository (the design your friend described):
 * every user action becomes an [EventEnvelope] that is (1) applied to the
 * local Room tables immediately, so the UI updates instantly with zero
 * network, and (2) queued in [OutboxEventDao] until the server confirms it.
 * The local tables ([ListEntity]/[ListItemEntity]) are just a "materialized
 * view" - the current snapshot you get by replaying every event applied so
 * far. There's no more per-row synced/existsOnServer flag; a row's sync
 * status is really a question about the outbox, not the row.
 *
 * Syncing ([syncPendingChanges]) is always the same two steps regardless of
 * why it's running (first login, reconnect, or right after an action):
 * push whatever's in the outbox, then pull everything that happened
 * elsewhere since our last successful sync and apply it. A brand-new
 * install has a cursor of 0, so its first pull naturally receives the
 * entire history and bootstraps its local database - there's no separate
 * "initial load" code path.
 */
class ListsRepository(
    private val listDao: ListDao,
    private val listItemDao: ListItemDao,
    private val outboxEventDao: OutboxEventDao,
    private val deviceIdProvider: DeviceIdProvider,
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
        val listId = UUID.randomUUID().toString()
        val sortOrder = listDao.maxSortOrder() + 1
        val owner = sessionManager.currentSession().username

        emit(EventType.LIST_CREATED, listId, mapOf("name" to name, "sortOrder" to sortOrder.toString(), "owner" to owner))
        if (color != null) {
            emit(EventType.LIST_COLOR_CHANGED, listId, mapOf("colorHex" to color.toHex()))
        }
        if (sharedWithUsername.isNotBlank()) {
            emit(EventType.LIST_SHARED_WITH_CHANGED, listId, mapOf("sharedWith" to sharedWithUsername))
        }

        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun addItem(serverUrl: String, accessToken: String, listId: String, itemName: String): Result<Unit> {
        val itemId = UUID.randomUUID().toString()
        emit(EventType.ITEM_ADDED, itemId, mapOf("listId" to listId, "name" to itemName))
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun setChecked(serverUrl: String, accessToken: String, itemId: String, checked: Boolean): Result<Unit> {
        emit(EventType.ITEM_CHECKED_CHANGED, itemId, mapOf("checked" to checked.toString()))
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun deleteList(serverUrl: String, accessToken: String, listId: String): Result<Unit> {
        emit(EventType.LIST_DELETED, listId, emptyMap())
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun deleteItem(serverUrl: String, accessToken: String, itemId: String): Result<Unit> {
        emit(EventType.ITEM_DELETED, itemId, emptyMap())
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun setColor(serverUrl: String, accessToken: String, listId: String, color: Color?): Result<Unit> {
        emit(EventType.LIST_COLOR_CHANGED, listId, mapOf("colorHex" to color?.toHex()))
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun updateSortOrder(serverUrl: String, accessToken: String, orderedListIds: List<String>): Result<Unit> {
        orderedListIds.forEachIndexed { index, id ->
            val existing = listDao.getById(id)
            if (existing != null && existing.sortOrder != index) {
                emit(EventType.LIST_REORDERED, id, mapOf("sortOrder" to index.toString()))
            }
        }
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun updateSharedWith(
        serverUrl: String,
        accessToken: String,
        listId: String,
        sharedWith: List<String>
    ): Result<Unit> {
        emit(EventType.LIST_SHARED_WITH_CHANGED, listId, mapOf("sharedWith" to sharedWith.joinToString("|")))
        return syncPendingChanges(serverUrl, accessToken)
    }

    suspend fun markSharedExternally(serverUrl: String, accessToken: String, listId: String): Result<Unit> {
        emit(EventType.LIST_SHARED_EXTERNALLY, listId, emptyMap())
        return syncPendingChanges(serverUrl, accessToken)
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

    // ---- The sync cycle: push our outbox, then pull + apply everyone else's. ----

    suspend fun syncPendingChanges(serverUrl: String, accessToken: String): Result<Unit> {
        return try {
            val client = api(serverUrl, accessToken)
            val deviceId = deviceIdProvider.getOrCreate()

            val outbox = outboxEventDao.getAll()
            if (outbox.isNotEmpty()) {
                val envelopes = outbox.map { it.toEnvelope() }
                val pushResponse = client.pushEvents(PushEventsRequest(deviceId, envelopes))
                if (!pushResponse.isSuccessful) {
                    return Result.failure(Exception("HTTP ${pushResponse.code()}"))
                }
                // Now durable on the server - this phone doesn't need to remember them anymore.
                outbox.forEach { outboxEventDao.deleteById(it.id) }
            }

            val since = syncStateManager.getLastSyncedAt()
            val pullResponse = client.pullEvents(since, deviceId)
            if (!pullResponse.isSuccessful) {
                return Result.failure(Exception("HTTP ${pullResponse.code()}"))
            }

            val body: PullEventsResponse = pullResponse.body() ?: return Result.failure(Exception("Empty response"))
            body.events.forEach { event ->
                // Assumes the server returns events in the order they actually
                // happened (so e.g. an ITEM_ADDED never arrives before its
                // list's LIST_CREATED) - if that's ever violated, skip just the
                // one bad event instead of aborting the whole sync.
                runCatching { applyEvent(event) }
            }
            syncStateManager.setLastSyncedAt(body.serverTime)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Builds an envelope, applies it locally right now, and queues it for the next push. */
    private suspend fun emit(type: String, entityId: String, payload: Map<String, String?>) {
        val envelope = EventEnvelope(
            id = UUID.randomUUID().toString(),
            type = type,
            entityId = entityId,
            payload = payload,
            timestamp = System.currentTimeMillis(),
            deviceId = deviceIdProvider.getOrCreate()
        )
        applyEvent(envelope)
        outboxEventDao.insert(envelope.toOutboxEntity())
    }

    /**
     * The reducer: turns one event into a local database change. This is the
     * one place that knows what each event type means - used both for events
     * we just created ourselves and events pulled from other devices, so
     * applying an event always has the exact same effect no matter which
     * phone originally produced it.
     */
    private suspend fun applyEvent(event: EventEnvelope) {
        when (event.type) {
            EventType.LIST_CREATED -> {
                val existing = listDao.getById(event.entityId)
                listDao.upsert(
                    ListEntity(
                        id = event.entityId,
                        name = event.payload["name"] ?: existing?.name ?: "",
                        owner = event.payload["owner"] ?: existing?.owner ?: "",
                        colorHex = existing?.colorHex,
                        sharedWith = existing?.sharedWith ?: emptyList(),
                        sharedExternally = existing?.sharedExternally ?: false,
                        sortOrder = event.payload["sortOrder"]?.toIntOrNull() ?: existing?.sortOrder ?: 0,
                        checkedSectionExpanded = existing?.checkedSectionExpanded ?: false
                    )
                )
            }
            EventType.LIST_RENAMED -> {
                listDao.getById(event.entityId)?.let { existing ->
                    listDao.upsert(existing.copy(name = event.payload["name"] ?: existing.name))
                }
            }
            EventType.LIST_DELETED -> listDao.deleteById(event.entityId)
            EventType.LIST_COLOR_CHANGED -> {
                listDao.getById(event.entityId)?.let { existing ->
                    listDao.upsert(existing.copy(colorHex = event.payload["colorHex"]))
                }
            }
            EventType.LIST_SHARED_WITH_CHANGED -> {
                val sharedWith = event.payload["sharedWith"]
                    ?.let { if (it.isBlank()) emptyList() else it.split("|") }
                    ?: emptyList()
                listDao.getById(event.entityId)?.let { existing ->
                    listDao.upsert(existing.copy(sharedWith = sharedWith))
                }
            }
            EventType.LIST_SHARED_EXTERNALLY -> {
                listDao.getById(event.entityId)?.let { existing ->
                    listDao.upsert(existing.copy(sharedExternally = true))
                }
            }
            EventType.LIST_REORDERED -> {
                val newOrder = event.payload["sortOrder"]?.toIntOrNull()
                if (newOrder != null) {
                    listDao.getById(event.entityId)?.let { existing ->
                        listDao.upsert(existing.copy(sortOrder = newOrder))
                    }
                }
            }
            EventType.ITEM_ADDED -> {
                val listId = event.payload["listId"] ?: return
                listItemDao.upsert(
                    ListItemEntity(
                        id = event.entityId,
                        listId = listId,
                        name = event.payload["name"] ?: "",
                        checked = false
                    )
                )
            }
            EventType.ITEM_CHECKED_CHANGED -> {
                val checked = event.payload["checked"]?.toBoolean() ?: false
                listItemDao.getById(event.entityId)?.let { existing ->
                    listItemDao.upsert(existing.copy(checked = checked))
                }
            }
            EventType.ITEM_DELETED -> listItemDao.deleteById(event.entityId)
        }
    }
}

private fun EventEnvelope.toOutboxEntity(): OutboxEventEntity = OutboxEventEntity(
    id = id, type = type, entityId = entityId, payload = payload, timestamp = timestamp, deviceId = deviceId
)

private fun OutboxEventEntity.toEnvelope(): EventEnvelope = EventEnvelope(
    id = id, type = type, entityId = entityId, payload = payload, timestamp = timestamp, deviceId = deviceId
)

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
