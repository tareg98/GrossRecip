package com.sirolf2009.grossrecipes.events

import com.sirolf2009.grossrecipes.events.entity.Event
import com.sirolf2009.modulith.cqrs.Command

/**
 * Everything this [owner] needs to catch up on: every event received by the
 * server after [since] (a receivedAt cursor, not the events' own original
 * client timestamps - see Event.receivedAt) that didn't originate from
 * [excludeDeviceId] (that phone already applied its own events locally the
 * moment it created them - sending them back would just be redundant),
 * oldest first. Ordering matters: the app assumes events arrive in the order
 * they actually happened (e.g. an item's ITEM_ADDED before its list's later
 * events) - see the design notes in the event-sourcing contract doc.
 *
 * The generic Read<T,K> helper used elsewhere in this codebase only supports
 * a single "field equals value" match, so this runs its own Hibernate query
 * instead - same pattern as GetAuthenticatedList doing more than plain CRUD.
 */
class PullEventsSince(
    private val owner: String,
    private val since: Long,
    private val excludeDeviceId: String
) : Command<List<Event>>() {

    override fun execute(): List<Event> {
        EventRepository.sessionFactory.openSession().use { session ->
            return session.createQuery(
                "FROM Event WHERE owner = :owner AND receivedAt > :since AND deviceId != :deviceId ORDER BY receivedAt ASC",
                Event::class.java
            )
                .setParameter("owner", owner)
                .setParameter("since", since)
                .setParameter("deviceId", excludeDeviceId)
                .list()
        }
    }
}
