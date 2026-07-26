package com.sirolf2009.grossrecipes.events.entity

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * One row = one thing that happened (a list renamed, an item checked, etc).
 * The server doesn't need to understand what [type]/[payload] mean - it just
 * stores and forwards the envelope untouched. All the meaning lives on the
 * Android side (see ListsRepository.applyEvent in the app). [id] is assigned
 * by the phone that created the event (not auto-generated here), so pushing
 * the same event twice is safe to detect and ignore - see PushEvent.
 *
 * [timestamp] is the PHONE's clock at the moment it created the event - it's
 * kept for reference but is deliberately NOT what pulls are ordered/filtered
 * by. A phone offline for three days pushes events whose [timestamp] is
 * three days old; if pulls filtered on that, other devices whose cursor had
 * already moved past "three days ago" would silently miss them forever.
 * [receivedAt] is the SERVER's clock at the moment it stored the event -
 * always caught up to "now" regardless of how stale the original device's
 * clock or connectivity was - so that's the field pulls actually use.
 */
@Entity
@Table(name = "events")
class Event(
    @Id
    var id: UUID,
    var owner: String = "",
    var type: String,
    @Column(name = "entity_id")
    var entityId: UUID,
    @Convert(converter = PayloadConverter::class)
    var payload: Map<String, String?> = emptyMap(),
    var timestamp: Long,
    @Column(name = "device_id")
    var deviceId: String,
    @Column(name = "received_at")
    var receivedAt: Long = 0L
)
