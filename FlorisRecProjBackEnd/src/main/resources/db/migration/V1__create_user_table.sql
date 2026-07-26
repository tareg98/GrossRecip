CREATE TABLE Users (
    id          VARCHAR(255) PRIMARY KEY,
    username    VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    salt        VARCHAR(255) NOT NULL
);