-- AIP PPC Assistant — auth schema (users + integration API clients).
-- Enums are stored as varchar (validated by the Java enum) rather than Postgres
-- native enums, so new values (e.g. departments, categories) never require an
-- out-of-transaction ALTER TYPE migration.

CREATE TABLE users (
    id              uuid PRIMARY KEY,
    email           varchar(255) NOT NULL UNIQUE,
    password_hash   varchar(255) NOT NULL,
    name            varchar(255) NOT NULL,
    role            varchar(32)  NOT NULL DEFAULT 'APPLICANT',
    organization    varchar(255),
    enabled         boolean      NOT NULL DEFAULT true,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE api_clients (
    id           uuid PRIMARY KEY,
    name         varchar(255) NOT NULL,
    key_hash     varchar(255) NOT NULL UNIQUE,
    key_prefix   varchar(255) NOT NULL,
    webhook_url  varchar(255),
    active       boolean      NOT NULL DEFAULT true,
    last_used_at timestamptz,
    created_at   timestamptz  NOT NULL DEFAULT now()
);
