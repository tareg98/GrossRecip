package com.sirolf2009.grossrecipes.list.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(name = "listitems")
class ListItem(
    @Id
    @GeneratedValue
    var id: UUID? = null,
    @Column(name = "list_id")
    var listId: UUID,
    var name: String,
    var checked: Boolean
)