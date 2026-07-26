package com.sirolf2009.grossrecipes.list.dto

import com.sirolf2009.grossrecipes.list.entity.List
import com.sirolf2009.grossrecipes.list.entity.ListItem

data class ListWithItemsDTO(
    val list: List,
    val items: kotlin.collections.List<ListItem>,
)