package com.sirolf2009.grossrecipes.list

import com.sirolf2009.grossrecipes.list.entity.ListItem
import com.sirolf2009.modulith.cqrs.query.Read
import com.sirolf2009.modulith.cqrs.query.RepositoryCRUD
import java.util.UUID

object ListItemRepository : RepositoryCRUD<ListItem, UUID>(
    ListModule.sessionFactory,
    ListItem::class.java,
) {

    fun getListsByListId(listId: UUID): Read<ListItem, String> {
        return Read(
            sessionFactory,
            "listId",
            listId,
            clazz
        )
    }

}