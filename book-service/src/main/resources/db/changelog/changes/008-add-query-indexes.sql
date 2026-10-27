-- liquibase formatted sql

-- changeset abondar:008
CREATE INDEX idx_book_author_author_id_book_id ON book_author (author_id, book_id);
CREATE INDEX idx_book_catalog_id ON book (catalog_id);
CREATE INDEX idx_author_sort_name_first_name ON author (sort_name, first_name);
CREATE INDEX idx_author_name_dob ON author (first_name, last_name, date_of_birth);

-- rollback DROP INDEX idx_author_name_dob;
-- rollback DROP INDEX idx_author_sort_name_first_name;
-- rollback DROP INDEX idx_book_catalog_id;
-- rollback DROP INDEX idx_book_author_author_id_book_id;
