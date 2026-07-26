package com.example.grossrecipes.data.local

// Superseded by OutboxEventEntity (see OutboxEventEntity.kt). Under event
// sourcing, a delete is just one more event type (LIST_DELETED/ITEM_DELETED)
// sitting in the same outbox as everything else, rather than its own
// separate pending-delete table. Kept as an empty file rather than deleted.
