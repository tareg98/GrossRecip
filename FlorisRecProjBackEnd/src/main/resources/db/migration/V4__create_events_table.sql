-- entity_id deliberately has NO foreign key: an event can be about either a
-- list or a list item (depending on `type`), and a single column can't
-- reference two different parent tables.
--
-- `timestamp` is the phone's own clock when it created the event - kept for
-- reference only. `received_at` is this server's clock when it stored the
-- event, and is what pulls actually filter/order by - a phone that was
-- offline for days still gets a received_at of "now" the moment it finally
-- pushes, so other devices' cursors (themselves based on received_at) can
-- never skip past it.
CREATE TABLE Events (
    id          UUID PRIMARY KEY,
    owner       VARCHAR(255) REFERENCES users(id),
    type        VARCHAR(255) NOT NULL,
    entity_id   UUID NOT NULL,
    payload     TEXT NOT NULL,
    timestamp   BIGINT NOT NULL,
    device_id   VARCHAR(255) NOT NULL,
    received_at BIGINT NOT NULL
);

-- Every pull is "give me this owner's events received after some cursor" -
-- this index is exactly that query.
CREATE INDEX idx_events_owner_received_at ON Events (owner, received_at);
