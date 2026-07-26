package com.sirolf2009.grossrecipes.list

import com.sirolf2009.grossrecipes.list.dto.AddListItemRequest
import com.sirolf2009.grossrecipes.list.dto.CreateListRequest
import com.sirolf2009.grossrecipes.list.dto.ListWithItemsDTO
import com.sirolf2009.grossrecipes.list.dto.SetCheckedRequest
import com.sirolf2009.grossrecipes.list.dto.UpdateListNameRequest
import com.sirolf2009.grossrecipes.list.entity.List
import com.sirolf2009.grossrecipes.list.entity.ListItem
import com.sirolf2009.modulith.account.GetAuthenticatedUser
import com.sirolf2009.modulith.cqrs.execute
import com.sirolf2009.modulith.cqrs.query.SessionFactoryMaker
import com.sirolf2009.modulith.module.GsonSingleton.gson
import com.sirolf2009.modulith.module.staticmodule.Delete
import com.sirolf2009.modulith.module.staticmodule.Get
import com.sirolf2009.modulith.module.staticmodule.Path
import com.sirolf2009.modulith.module.staticmodule.Post
import com.sirolf2009.modulith.module.staticmodule.StaticModule
import spark.kotlin.RouteHandler
import java.io.File
import java.time.ZonedDateTime
import java.util.UUID

@Path ("Lists")
object ListModule : StaticModule {

    val sessionFactory = SessionFactoryMaker(
        File("src/main/resources/hibernate.cfg.xml"),
        listOf(List::class.java, ListItem::class.java),
    ).factory

    @Get("/my-lists")
    fun RouteHandler.myLists(): kotlin.collections.List<ListWithItemsDTO> {
        val user = execute(GetAuthenticatedUser(request))

        return execute(ListRepository.getListsByOwner(user.userId)).map { list ->
            val items = execute(ListItemRepository.getListsByListId(list.id!!))
            ListWithItemsDTO(list, items)
        }
    }

    @Post("/create")
    fun RouteHandler.createList(): List {
        val user = execute(GetAuthenticatedUser(request))

        val newList = execute(ListRepository.create(List(
            owner = user.userId,
            name = "New List",
            createdAt = ZonedDateTime.now(),
        )))
        return newList
    }

    @Post("/update-name")
    fun RouteHandler.updateName() {
        val updateNameRequest = gson.fromJson(request.body(), UpdateListNameRequest::class.java)

        val list = execute(GetAuthenticatedList(request, updateNameRequest.id))

        list.name = updateNameRequest.name

        execute(ListRepository.update(list))
    }

    @Post("/add-item")
    fun RouteHandler.addItem(): ListItem {
        val addListItemRequest = gson.fromJson(request.body(), AddListItemRequest::class.java)

        val list = execute(GetAuthenticatedList(request, addListItemRequest.listId))
        val listItem = ListItem(
            listId = list.id!!,
            name = addListItemRequest.itemName,
            checked = false
        )

        return execute(ListItemRepository.create(listItem))
    }

    @Delete("/delete/:id")
    fun RouteHandler.delete(): List {
        val id = UUID.fromString(request.params("id"))

        val list = execute(GetAuthenticatedList(request, id))

        return execute(ListRepository.delete(list))
    }

    @Post("/set-checked")
    fun RouteHandler.setChecked(): ListItem {
        val setCheckedRequest = gson.fromJson(request.body(), SetCheckedRequest::class.java)

        val listItem = execute(ListItemRepository.read(setCheckedRequest.itemId)).orElseThrow {
            NoSuchElementException("Could not find listItem with id ${setCheckedRequest.itemId}")
        }
        execute(GetAuthenticatedList(request, listItem.listId))

        listItem.checked = setCheckedRequest.isChecked

        return execute(ListItemRepository.update(listItem))
    }

}