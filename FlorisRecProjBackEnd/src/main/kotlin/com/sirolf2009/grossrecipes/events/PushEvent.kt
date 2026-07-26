package com.sirolf2009.grossrecipes.events

import com.sirolf2009.grossrecipes.events.entity.Event
import com.sirolf2009.modulith.cqrs.Command
import com.sirolf2009.modulith.cqrs.execute

/**
 * Records one event as durably received, but only once: if a phone retries a
 * push after an ambiguous network timeout, the same event id may arrive
 * twice - this must be a no-op the second time, not an error or a duplicate
 * row (the app relies on this idempotency guarantee).
 *
 * [owner] is stamped from the authenticated request, never trusted from the
 * client - this is what scopes every event to one account, the same way
 * List.owner scopes lists today.
 */
class PushEvent(
    private val owner: String,
    private val event: Event
) : Command<Unit>() {

    override fun execute() {
        val alreadyStored = execute(EventRepository.read(event.id)).isPresent
        if (alreadyStored) return

        event.owner = owner
        // Stamped here, on the server, at the moment it's actually durable -
        // never trust the phone's clock for this. See the comment on
        // Event.receivedAt for why pulls are ordered by this, not [timestamp].
        event.receivedAt = System.currentTimeMillis()
        execute(EventRepository.create(event))
    }
}
