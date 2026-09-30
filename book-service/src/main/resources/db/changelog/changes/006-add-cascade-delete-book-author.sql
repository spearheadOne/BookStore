-- liquibase formatted sql

-- changeset abondar:006
ALTER TABLE book_author
    DROP CONSTRAINT book_author_author_id_fkey;

ALTER TABLE book_author
    DROP CONSTRAINT book_author_book_id_fkey;

ALTER TABLE book_author
    ADD CONSTRAINT fk_book_author_author
        FOREIGN KEY (author_id)
            REFERENCES author (id)
            ON DELETE CASCADE;

ALTER TABLE book_author
    ADD CONSTRAINT fk_book_author_book
        FOREIGN KEY (book_id)
            REFERENCES book (id)
            ON DELETE CASCADE;


-- rollback ALTER TABLE book_author DROP CONSTRAINT fk_book_author_author;
-- rollback ALTER TABLE book_author DROP CONSTRAINT fk_book_author_book;
-- rollback ALTER TABLE book_author ADD CONSTRAINT book_author_author_id_fkey FOREIGN KEY (author_id) REFERENCES author(id);
-- rollback ALTER TABLE book_author ADD CONSTRAINT book_author_book_id_fkey FOREIGN KEY (book_id) REFERENCES book(id);
