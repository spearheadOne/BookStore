--liquibase formatted sql

--changeset abondar:005
ALTER TABLE book
    ADD COLUMN catalog_id UUID REFERENCES catalog (id);


--
rollback ALTER TABLE book DROP COLUMN catalog_id;