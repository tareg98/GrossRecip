CREATE TABLE Lists (
    id          UUID PRIMARY KEY,
    owner       VARCHAR(255) REFERENCES users(id),
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);