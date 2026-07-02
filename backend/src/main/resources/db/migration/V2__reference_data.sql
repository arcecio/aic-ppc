-- Reference / configuration schema: permit types, parcels (GIS stand-in),
-- the regulatory knowledgebase, and the two configurable rule engines.

CREATE TABLE permit_types (
    id                  uuid PRIMARY KEY,
    code                varchar(64)  NOT NULL UNIQUE,
    name                varchar(255) NOT NULL,
    category            varchar(32)  NOT NULL DEFAULT 'OTHER',
    description         text,
    form_schema_json    text,
    required_docs_json  text,
    active              boolean      NOT NULL DEFAULT true,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE parcels (
    id                    uuid PRIMARY KEY,
    apn                   varchar(32)  NOT NULL UNIQUE,
    address               varchar(255) NOT NULL,
    address_normalized    varchar(255) NOT NULL,
    zone                  varchar(32),
    general_plan_land_use varchar(255),
    overlays_json         text,
    hazard_zones_json     text,
    council_district      integer,
    community_plan_area   varchar(255),
    latitude              double precision,
    longitude             double precision
);
CREATE INDEX idx_parcels_address_norm ON parcels (address_normalized);

CREATE TABLE regulatory_codes (
    id           uuid PRIMARY KEY,
    external_id  varchar(128) NOT NULL UNIQUE,
    jurisdiction varchar(16)  NOT NULL DEFAULT 'CITY_LA',
    code_type    varchar(64)  NOT NULL,
    section      varchar(64)  NOT NULL,
    title        varchar(255) NOT NULL,
    summary      text,
    url          varchar(255),
    tags         text,
    version      varchar(32),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_regcodes_code_type ON regulatory_codes (code_type);

CREATE TABLE screening_rules (
    id                       uuid PRIMARY KEY,
    code                     varchar(64)  NOT NULL UNIQUE,
    name                     varchar(255) NOT NULL,
    category                 varchar(24)  NOT NULL DEFAULT 'GENERAL',
    severity                 varchar(16)  NOT NULL DEFAULT 'WARNING',
    condition_json           text         NOT NULL,
    message                  text         NOT NULL,
    recommendation           text,
    code_reference           varchar(255),
    code_url                 varchar(255),
    confidence               integer      NOT NULL DEFAULT 90,
    applies_to_permit_types  varchar(255),
    priority                 integer      NOT NULL DEFAULT 100,
    active                   boolean      NOT NULL DEFAULT true,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_screening_rules_active ON screening_rules (active, priority);

CREATE TABLE clearance_rules (
    id                          uuid PRIMARY KEY,
    code                        varchar(64)  NOT NULL UNIQUE,
    department                  varchar(24)  NOT NULL DEFAULT 'LADBS',
    clearance_name              varchar(255) NOT NULL,
    condition_json              text         NOT NULL,
    reason                      text         NOT NULL,
    submittal_requirements_json text,
    info_url                    varchar(255),
    confidence                  integer      NOT NULL DEFAULT 80,
    applies_to_permit_types     varchar(255),
    priority                    integer      NOT NULL DEFAULT 100,
    active                      boolean      NOT NULL DEFAULT true,
    created_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_at                  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_clearance_rules_active ON clearance_rules (active, priority);
