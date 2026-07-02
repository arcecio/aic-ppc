-- Transactional schema: projects, uploaded documents, screening runs and their
-- findings + clearances, plus the feedback inbox and the append-only audit log.

CREATE TABLE projects (
    id                        uuid PRIMARY KEY,
    universal_project_id      varchar(32)  NOT NULL UNIQUE,
    owner_id                  uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title                     varchar(255) NOT NULL,
    permit_type_code          varchar(64)  NOT NULL,
    project_scope             text,
    intended_use              varchar(255),
    description               text,
    address                   varchar(255),
    apn                       varchar(32),
    parcel_id                 uuid REFERENCES parcels(id) ON DELETE SET NULL,
    form_data_json            text,
    status                    varchar(32)  NOT NULL DEFAULT 'DRAFT',
    current_readiness_score   integer,
    current_readiness_status  varchar(32)  DEFAULT 'NOT_ASSESSED',
    used_aip_ppc              boolean      NOT NULL DEFAULT true,
    submitted_to_eplanla_at   timestamptz,
    created_at                timestamptz  NOT NULL DEFAULT now(),
    updated_at                timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_projects_owner ON projects (owner_id);

CREATE TABLE documents (
    id                    uuid PRIMARY KEY,
    project_id            uuid         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    original_name         varchar(255) NOT NULL,
    content_type          varchar(255),
    file_type             varchar(16)  NOT NULL,
    size_bytes            bigint       NOT NULL,
    storage_path          varchar(255) NOT NULL,
    doc_category          varchar(64),
    scan_status           varchar(16)  NOT NULL DEFAULT 'PENDING',
    scan_detail           varchar(255),
    version               integer      NOT NULL DEFAULT 1,
    extracted_text_chars  integer      NOT NULL DEFAULT 0,
    uploaded_by           uuid,
    uploaded_at           timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_project ON documents (project_id);

CREATE TABLE precheck_runs (
    id               uuid PRIMARY KEY,
    project_id       uuid        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    status           varchar(16) NOT NULL DEFAULT 'PENDING',
    readiness_score  integer,
    readiness_status varchar(32),
    summary          text,
    finding_count    integer     NOT NULL DEFAULT 0,
    blocking_count   integer     NOT NULL DEFAULT 0,
    warning_count    integer     NOT NULL DEFAULT 0,
    info_count       integer     NOT NULL DEFAULT 0,
    clearance_count  integer     NOT NULL DEFAULT 0,
    processing_ms    bigint,
    ai_provider_used varchar(32),
    ai_model_used    varchar(255),
    code_version     varchar(32),
    triggered_by     varchar(16) NOT NULL DEFAULT 'APPLICANT',
    error_message    text,
    started_at       timestamptz,
    completed_at     timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_runs_project ON precheck_runs (project_id);
CREATE INDEX idx_runs_status ON precheck_runs (status);

CREATE TABLE findings (
    id                     uuid PRIMARY KEY,
    run_id                 uuid         NOT NULL REFERENCES precheck_runs(id) ON DELETE CASCADE,
    category               varchar(24)  NOT NULL DEFAULT 'GENERAL',
    severity               varchar(16)  NOT NULL DEFAULT 'WARNING',
    title                  varchar(255) NOT NULL,
    description            text         NOT NULL,
    code_reference         varchar(255),
    code_url               varchar(255),
    confidence             integer      NOT NULL DEFAULT 90,
    confidence_level       varchar(8)   NOT NULL DEFAULT 'HIGH',
    triggering_condition   text,
    assumptions            text,
    recommendation         text,
    source                 varchar(16)  NOT NULL DEFAULT 'RULE',
    rule_code              varchar(64),
    page_number            integer,
    location_x             double precision,
    location_y             double precision,
    location_width         double precision,
    location_height        double precision,
    staff_disposition      varchar(16)  NOT NULL DEFAULT 'PENDING',
    staff_comment          text,
    staff_reviewed_by      uuid,
    staff_reviewed_at      timestamptz,
    applicant_flagged      boolean      NOT NULL DEFAULT false,
    applicant_flag_comment text,
    created_at             timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_findings_run ON findings (run_id);

CREATE TABLE clearances (
    id                          uuid PRIMARY KEY,
    run_id                      uuid         NOT NULL REFERENCES precheck_runs(id) ON DELETE CASCADE,
    department                  varchar(24)  NOT NULL DEFAULT 'LADBS',
    clearance_name              varchar(255) NOT NULL,
    reason                      text         NOT NULL,
    confidence                  integer      NOT NULL DEFAULT 80,
    confidence_level            varchar(8)   NOT NULL DEFAULT 'MEDIUM',
    submittal_requirements_json text,
    info_url                    varchar(255),
    source                      varchar(16)  NOT NULL DEFAULT 'RULE',
    rule_code                   varchar(64),
    staff_disposition           varchar(16)  NOT NULL DEFAULT 'PENDING',
    staff_comment               text,
    staff_reviewed_by           uuid,
    staff_reviewed_at           timestamptz,
    created_at                  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_clearances_run ON clearances (run_id);

CREATE TABLE feedback_entries (
    id            uuid PRIMARY KEY,
    run_id        uuid,
    finding_id    uuid,
    clearance_id  uuid,
    submitted_by  uuid,
    submitter_role varchar(16),
    type          varchar(32)  NOT NULL,
    comment       text         NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'OPEN',
    created_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id          uuid PRIMARY KEY,
    actor_type  varchar(16)  NOT NULL,
    actor_id    varchar(255),
    actor_label varchar(255),
    action      varchar(64)  NOT NULL,
    entity_type varchar(64),
    entity_id   varchar(255),
    detail      text,
    ip_address  varchar(64),
    created_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_log (created_at DESC);
CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id);
