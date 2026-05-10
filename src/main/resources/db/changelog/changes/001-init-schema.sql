--liquibase formatted sql

--changeset radeflex:1
CREATE TABLE IF NOT EXISTS task(
    id SERIAL PRIMARY KEY,
    file_path VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);