package com.sirolf2009.grossrecipes.list.dto

import java.util.UUID

data class SetCheckedRequest(
    val itemId: UUID,
    val isChecked: Boolean
)