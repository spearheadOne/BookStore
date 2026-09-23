--liquibase formatted sql

--changeset abondar:002
CREATE TABLE author
(
    id            UUID PRIMARY KEY,
    first_name    TEXT        NOT NULL,
    last_name     TEXT        NOT NULL,
    sort_name     TEXT,
    date_of_birth DATE,
    date_of_death DATE,
    country_code  TEXT,
    biography     TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_author_country_code
        CHECK (country_code ~ '^[A-Z]{2}$'),

    CONSTRAINT chk_author_lifespan
        CHECK (date_of_death IS NULL OR date_of_death >= date_of_birth)
);
--rollback DROP TABLE author;