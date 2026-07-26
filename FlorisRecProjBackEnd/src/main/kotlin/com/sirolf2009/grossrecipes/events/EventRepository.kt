package com.sirolf2009.grossrecipes.events

import com.sirolf2009.grossrecipes.events.entity.Event
import com.sirolf2009.modulith.cqrs.query.RepositoryCRUD
import java.util.UUID

object EventRepository : RepositoryCRUD<Event, UUID>(
    EventsModule.sessionFactory,
    Event::class.java,
)
