package com.sirolf2009.grossrecipes.list.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(name = "lists")
class List(
    @Id
    @GeneratedValue
    var id: UUID? = null,
    var owner: String,
    var name: String,
    @Column("created_at")
    var createdAt: ZonedDateTime
)