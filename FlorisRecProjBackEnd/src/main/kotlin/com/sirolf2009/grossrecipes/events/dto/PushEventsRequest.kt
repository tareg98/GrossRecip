package com.sirolf2009.grossrecipes.events.dto

import com.sirolf2009.grossrecipes.events.entity.Event

data class PushEventsRequest(
    val deviceId: String,
    val events: List<Event>,
)
