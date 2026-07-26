package com.sirolf2009.grossrecipes.events.dto

import com.sirolf2009.grossrecipes.events.entity.Event

data class PullEventsResponse(
    val events: List<Event>,
    // The phone stores this as its new cursor instead of trusting its own
    // clock, so a pull is never lossy even if the phone's clock is off.
    val serverTime: Long,
)
