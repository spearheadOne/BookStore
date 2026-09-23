--liquibase formatted sql

--changeset abondar:004
CREATE TABLE catalog
(
    id   UUID PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--
rollback DROP TABLE catalog;