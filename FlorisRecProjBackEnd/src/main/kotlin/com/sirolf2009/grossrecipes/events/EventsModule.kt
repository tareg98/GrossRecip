package com.sirolf2009.grossrecipes.events

import com.sirolf2009.grossrecipes.events.dto.PullEventsResponse
import com.sirolf2009.grossrecipes.events.dto.PushEventsRequest
import com.sirolf2009.grossrecipes.events.entity.Event
import com.sirolf2009.modulith.account.GetAuthenticatedUser
import com.sirolf2009.modulith.cqrs.execute
import com.sirolf2009.modulith.cqrs.query.SessionFactoryMaker
import com.sirolf2009.modulith.module.GsonSingleton.gson
import com.sirolf2009.modulith.module.staticmodule.Get
import com.sirolf2009.modulith.module.staticmodule.Path
import com.sirolf2009.modulith.module.staticmodule.Post
import com.sirolf2009.modulith.module.staticmodule.StaticModule
import spark.kotlin.RouteHandler
import java.io.File

/**
 * Replaces the old per-action Lists routes entirely (see ListModule -
 * that whole module is superseded by this one plus the Android app's local
 * database). Every list/item action the app makes is now one of these two
 * generic calls instead of its own bespoke route. Full contract - request/
 * response shapes, the event type catalog, ordering/idempotency assumptions
 * - is documented in GrossRecipes_Event_Sourcing_Contract.docx on the
 * Android side.
 */
@Path("Events")
object EventsModule : StaticModule {

    val sessionFactory = SessionFactoryMaker(
        File("src/main/resources/hibernate.cfg.xml"),
        listOf(Event::class.java),
    ).factory

    @Post("/push")
    fun RouteHandler.push() {
        val user = execute(GetAuthenticatedUser(request))
        val pushRequest = gson.fromJson(request.body(), PushEventsRequest::class.java)

        pushRequest.events.forEach { event ->
            execute(PushEvent(user.userId, event))
        }
    }

    @Get("/pull")
    fun RouteHandler.pull(): PullEventsResponse {
        val user = execute(GetAuthenticatedUser(request))
        val since = request.queryParams("since")?.toLongOrNull() ?: 0L
        val deviceId = request.queryParams("deviceId") ?: ""

        val events = execute(PullEventsSince(user.userId, since, deviceId))
        // The next cursor is the last event actually delivered - not "now".
        // Using the current clock here would create a race: an event being
        // pushed by someone else at this exact instant could land with a
        // receivedAt between this query and "now", and a cursor of "now"
        // would skip straight past it forever. Falling back to the same
        // `since` when nothing came back means an empty pull never
        // accidentally advances the cursor.
        val nextCursor = events.maxOfOrNull { it.receivedAt } ?: since
        return PullEventsResponse(events, nextCursor)
    }

}
