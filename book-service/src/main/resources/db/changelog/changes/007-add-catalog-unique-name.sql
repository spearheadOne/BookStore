-- liquibase formatted sql

-- changeset abondar:007
ALTER TABLE catalog
    ADD CONSTRAINT uq_catalog_name UNIQUE (name);

-- rollback ALTER TABLE catalog DROP CONSTRAINT uq_catalog_name;
