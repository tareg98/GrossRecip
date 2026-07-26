package com.example.grossrecipes.data.dto

/**
 * Event sourcing: every change the user makes (check an item, rename a list,
 * etc.) is represented as one of these instead of a direct "save this row"
 * call. The phone only has to hold onto envelopes it hasn't successfully
 * pushed yet (see OutboxEventEntity) - once the server has acknowledged them,
 * they're discarded. The server is the one place that keeps the full history.
 *
 * [payload] is intentionally all-String key/value pairs (never raw numbers or
 * booleans) so there's no ambiguity across JSON libraries about how a field
 * round-trips - the reducer that applies the event parses whatever it needs
 * (see ListsRepository.applyEvent). See [EventType] for which keys each event
 * type carries.
 */
data class EventEnvelope(
    val id: String,
    val type: String,
    val entityId: String,
    val payload: Map<String, String?>,
    val timestamp: Long,
    val deviceId: String
)

/** The full set of event types this app produces and understands. */
object EventType {
    // entityId = listId
    const val LIST_CREATED = "LIST_CREATED"           // payload: name, sortOrder
    const val LIST_RENAMED = "LIST_RENAMED"           // payload: name
    const val LIST_DELETED = "LIST_DELETED"           // payload: (none)
    const val LIST_COLOR_CHANGED = "LIST_COLOR_CHANGED"           // payload: colorHex (may be null/absent)
    const val LIST_SHARED_WITH_CHANGED = "LIST_SHARED_WITH_CHANGED"   // payload: sharedWith (pipe-joined)
    const val LIST_SHARED_EXTERNALLY = "LIST_SHARED_EXTERNALLY"     // payload: (none)
    const val LIST_REORDERED = "LIST_REORDERED"         // payload: sortOrder

    // entityId = itemId
    const val ITEM_ADDED = "ITEM_ADDED"             // payload: listId, name
    const val ITEM_CHECKED_CHANGED = "ITEM_CHECKED_CHANGED"       // payload: checked ("true"/"false")
    const val ITEM_DELETED = "ITEM_DELETED"           // payload: (none)
}

/** Body of `POST Events/push`. */
data class PushEventsRequest(
    val deviceId: String,
    val events: List<EventEnvelope>
)

/** Body of the response from `GET Events/pull`. */
data class PullEventsResponse(
    val events: List<EventEnvelope>,
    // Server's clock at the time it answered - the client stores this as its
    // new cursor instead of trusting its own clock, so pull is never lossy
    // even if the phone's clock is off.
    val serverTime: Long
)
