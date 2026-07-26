CREATE TABLE ListItems (
    id          UUID PRIMARY KEY,
    list_id     UUID REFERENCES lists(id),
    name        VARCHAR(255) NOT NULL,
    checked     BOOLEAN NOT NULL
);