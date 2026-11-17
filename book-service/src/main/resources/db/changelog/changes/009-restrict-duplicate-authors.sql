-- liquibase formatted sql

-- changeset abondar:009

ALTER TABLE author
    ADD CONSTRAINT uq_author UNIQUE NULLS NOT DISTINCT (first_name, last_name, date_of_birth);


-- rollback ALTER TABLE author DROP CONSTRAINT uq_author;
