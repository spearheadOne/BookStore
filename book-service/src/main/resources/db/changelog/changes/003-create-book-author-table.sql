--liquibase formatted sql

--changeset abondar:003
CREATE TABLE book_author
(
    book_id      UUID    NOT NULL REFERENCES book (id),
    author_id    UUID    NOT NULL REFERENCES author (id),
    author_order INTEGER NOT NULL,

    PRIMARY KEY (book_id, author_id),

    CONSTRAINT chk_book_author_order
        CHECK (author_order > 0),

    CONSTRAINT uq_book_author_order
        UNIQUE (book_id, author_order)
);

--
rollback DROP TABLE book_author;