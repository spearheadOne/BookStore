--liquibase formatted sql

--changeset abondar:001
CREATE TABLE book
(
    id    UUID PRIMARY KEY,
    isbn  VARCHAR(17)  NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL
);

--
rollback DROP TABLE book;