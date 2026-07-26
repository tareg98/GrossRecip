package com.sirolf2009.grossrecipes.list

import com.sirolf2009.grossrecipes.list.entity.List
import com.sirolf2009.modulith.cqrs.query.Read
import com.sirolf2009.modulith.cqrs.query.RepositoryCRUD
import java.util.UUID

object ListRepository : RepositoryCRUD<List, UUID>(
    ListModule.sessionFactory,
    List::class.java,
) {

    fun getListsByOwner(owner: String): Read<List, String> {
        return Read<List,String>(
            sessionFactory,
            "owner",
            owner,
            clazz
        )
    }

}