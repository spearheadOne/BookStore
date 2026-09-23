--liquibase formatted sql

--changeset abondar:001
CREATE TABLE book
(
    id    UUID PRIMARY KEY,
    isbn        TEXT NOT NULL UNIQUE,
    title       TEXT NOT NULL,
    description TEXT,
    issue_year  INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP

);

--
rollback DROP TABLE book;