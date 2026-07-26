package com.sirolf2009.grossrecipes.list.dto

import java.util.UUID

data class AddListItemRequest(
    val listId: UUID,
    val itemName: String,
)