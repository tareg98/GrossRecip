package com.sirolf2009.grossrecipes.list

import com.sirolf2009.grossrecipes.list.entity.List
import com.sirolf2009.modulith.account.GetAuthenticatedUser
import com.sirolf2009.modulith.cqrs.Command
import spark.Request
import java.util.UUID

class GetAuthenticatedList(
    val request: Request,
    val id: UUID
) : Command<List>() {

    override fun execute(): List {
        val user = execute(GetAuthenticatedUser(request))

        val list = execute(ListRepository.read(id)).orElseThrow {
            NoSuchElementException("List with id $id not found")
        }
        require(list.owner == user.userId) { "You don't have the rights to access list $id" }

        return list
    }

}