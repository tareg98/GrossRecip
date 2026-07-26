package com.sirolf2009.grossrecipes.list.dto

import java.util.UUID

data class UpdateListNameRequest(
    val id: UUID,
    val name: String,
)