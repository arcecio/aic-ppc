# 03 — Domain Model

City of Los Angeles / LADBS — **AI-Powered Pre-Plan Check Assistant** (RFP 2025AIP007)

This document describes every persisted entity, its key fields and relationships, and
the enum vocabulary. Everything here is backed by JPA `@Entity` classes under
`backend/src/main/java/com/lacity/aipppc/model/` and the Flyway migrations under
`backend/src/main/resources/db/migration/` (`V1__init_auth.sql`, `V2__reference_data.sql`,
`V3__projects.sql`, `V4__pgvector_embeddings.sql`, `V5__audit_snapshots.sql`). JPA runs with
`ddl-auto: validate`, so the Java entities and the SQL schema are kept in lockstep — the one
deliberate exception is the `regulatory_codes.embedding` pgvector column (added in `V4`), which
is managed by native SQL rather than a mapped JPA field.

A design decision worth calling out: **all enums are stored as `varchar`, validated by the
Java enum**, not as native Postgres enum types. New values (a new department, a new finding
category) never require an out-of-transaction `ALTER TYPE` migration.

---

## ER overview (ASCII)

```
                         ┌───────────┐         ┌────────────┐
                         │   User    │         │ ApiClient  │
                         │ (people)  │         │(integrations)
                         └─────┬─────┘         └────────────┘
                               │ owns
                               ▼
   ┌──────────────┐      ┌───────────┐       ┌───────────┐
   │  PermitType  │◀─────│  Project  │──────▶│  Parcel   │
   │ (form+docs)  │ code │  (UPID)   │ FK    │ (GIS)     │
   └──────────────┘      └─────┬─────┘       └───────────┘
                               │ 1..*                 ▲
              ┌────────────────┼──────────────┐       │ resolved at intake
              ▼                ▼               ▼       │
        ┌──────────┐    ┌──────────────┐  (form_data_json answers feed context)
        │ Document │    │ PreCheckRun  │
        │(uploads) │    │ (screening)  │
        └──────────┘    └──────┬───────┘
                               │ 1..*
                    ┌──────────┴──────────┐
                    ▼                     ▼
              ┌──────────┐          ┌────────────┐
              │ Finding  │          │ Clearance  │
              └──────────┘          └────────────┘

   Configuration / knowledgebase (staff-editable, seeded on boot):
   ┌───────────────┐  ┌───────────────┐  ┌──────────────────┐
   │ ScreeningRule │  │ ClearanceRule │  │ RegulatoryCode   │
   └───────────────┘  └───────────────┘  └──────────────────┘

   Cross-cutting:
   ┌───────────────┐  ┌──────────────┐
   │ FeedbackEntry │  │  AuditLog    │  (append-only)
   └───────────────┘  └──────────────┘
```

Findings and clearances belong to a **run**, not directly to a project, so every screening
execution keeps its own immutable result set — this is what supports version comparison
across resubmittals.

---

## Two cross-cutting concepts

### Universal Project ID (UPID)
Every `Project` is assigned a **BuildLA-style Universal Project ID** at intake, formatted
`LA-<year>-<6 digits>` (e.g. `LA-2026-000123`), generated in `ProjectService.generateUniversalProjectId()`
using `SecureRandom` with a uniqueness retry loop. It is unique across the system and is
designed to follow the project into Formal Plan Check, so pre-check results can be linked to
the eventual ePlanLA/LACPS submission. The UPID is stamped onto the PDF report and every
integration/webhook payload.

### Readiness status & score
A screening run produces a **readiness score (0–100)** and a **readiness status**
(`ReadinessStatus`). The latest values are also denormalized onto the `Project`
(`current_readiness_score`, `current_readiness_status`) for fast dashboard rendering. Score
and status are advisory notifications to the applicant — they never gate submission. The
scoring formula lives in `ScreeningService.computeScore()` / `computeStatus()` and is
documented in `02-architecture.md`.

---

## Entities

### User — `users`
A person using the assistant. Roles map to the RFP's user classes; in production applicants
sign in through the City's Angeleno Account (Auth0) and staff through Okta SSO — the MVP
issues its own JWT for the same identities.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `email` | varchar, unique | Login identity (normalized lower-case) |
| `passwordHash` | varchar | BCrypt |
| `name` | varchar | |
| `role` | enum `Role` | `APPLICANT` \| `STAFF` \| `ADMIN` |
| `organization` | varchar, nullable | Architect / developer firm for applicants |
| `enabled` | boolean | |
| `createdAt` / `updatedAt` | timestamptz | |

`isStaff()` returns true for `STAFF` and `ADMIN` (gates the staff Review & Analytics mode);
`isAdmin()` is `ADMIN` only. The `Role` enum is nested on `User` (`User.Role`).

### ApiClient — `api_clients`
A registered machine integration (ePlanLA, LACPS, other City systems) that calls the
`/api/v1` surface with an `X-API-Key` header.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `name` | varchar | Display name / audit label |
| `keyHash` | varchar, unique | **SHA-256 of the raw key only** — the plaintext key is shown once at creation |
| `keyPrefix` | varchar | Non-secret prefix for UI/audit identification |
| `webhookUrl` | varchar, nullable | Receives async `screening.completed` callbacks |
| `active` | boolean | |
| `lastUsedAt` | timestamptz, nullable | Stamped by `ApiKeyAuthFilter` on each authenticated call |
| `createdAt` | timestamptz | |

### PermitType — `permit_types`
A selectable permit / project type. Drives the dynamic intake form and the completeness
checklist. Both JSON columns are **staff-configurable data** — no code changes needed.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `code` | varchar, unique | e.g. `SFD_NEW`, `MULTIFAMILY_NEW`, `COMMERCIAL_NEW`, `COMMERCIAL_TI`, `ADU`, `SIGN`, `SOLAR_EV` |
| `name` | varchar | |
| `category` | enum `PermitCategory` | `RESIDENTIAL` \| `MULTI_FAMILY` \| `COMMERCIAL` \| `OTHER` |
| `description` | text | |
| `formSchemaJson` | text (JSON array) | Dynamic field descriptors (see below) |
| `requiredDocsJson` | text (JSON array) | Required-document checklist (see below) |
| `active` | boolean | |

**`formSchemaJson`** is an array of field descriptors; each becomes a context key at
screening time. Example (from `seed/permit-types.json`, `MULTIFAMILY_NEW`):

```json
[
  { "id": "units", "label": "Number of dwelling units", "type": "number", "required": true },
  { "id": "stories", "label": "Number of stories", "type": "number", "required": true },
  { "id": "includesAffordable", "label": "Includes affordable / density-bonus units?", "type": "boolean" },
  { "id": "parkingSpaces", "label": "Proposed parking spaces", "type": "number" }
]
```
Field types seen in the seed: `number`, `boolean`, `select` (with `options`). Some fields
carry a `showIf` hint (e.g. `gradingCubicYards` shows only for hillside parcels) — the
show/hide logic is a frontend concern driven by this schema.

**`requiredDocsJson`** is an array of `{ docKey, label, required, description? }`. The
`docKey` is what an uploaded `Document.docCategory` must match for completeness to consider
it present. Seed doc keys include `architectural_plans`, `structural_plans`,
`structural_calcs`, `title24_energy`, `green_code`, `soils_report`, `accessibility_plans`,
`mep_plans`, `site_survey`.

### Parcel — `parcels`
A parcel record standing in for the City's authoritative GIS sources (ZIMAS / NavigateLA).
Address validation and zoning/overlay/hazard lookup during intake resolve against these rows.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `apn` | varchar, unique | Assessor Parcel Number — canonical key |
| `address` | varchar | |
| `addressNormalized` | varchar (indexed) | Upper-cased, single-spaced for lookup |
| `zone` | varchar | e.g. `R1`, `C2`, `[Q]R4` |
| `generalPlanLandUse` | varchar | |
| `overlaysJson` | text (JSON string array) | e.g. `["Hillside","Coastal Zone","Very High Fire Hazard Severity Zone","Transit Oriented Communities Tier 3"]` |
| `hazardZonesJson` | text (JSON string array) | e.g. `["Methane","Liquefaction","Fault"]` |
| `councilDistrict` | integer, nullable | |
| `communityPlanArea` | varchar, nullable | |
| `latitude` / `longitude` | double, nullable | |

Overlays and hazard zones are the primary drivers of many screening and clearance rules
(hillside grading, fire WUI, methane mitigation, coastal development permit, HPOZ, TOC).

### RegulatoryCode — `regulatory_codes`
A structured entry in the regulatory knowledgebase — a code section from the LAMC, Title 24,
CBC, or the LADBS Clearance Summary Handbook. Findings link to these to supply the specific
code reference and link.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `externalId` | varchar, unique | Stable id for idempotent re-seeding, e.g. `LAMC-12.21-C` |
| `jurisdiction` | enum `Jurisdiction` | `CITY_LA` \| `COUNTY_LA` \| `STATE_CA` \| `FEDERAL` |
| `codeType` | varchar (indexed) | Code family, e.g. `LAMC Ch.I Zoning`, `Title 24`, `Clearance Handbook` |
| `section` | varchar | |
| `title` | varchar | |
| `summary` | text | |
| `url` | varchar | Resolved onto findings for the "code link" requirement |
| `tags` | text | Free-form keywords for the lexical retriever |
| `version` | varchar | Which code edition was applied |
| `updatedAt` | timestamptz | |

In addition to the columns above, migration `V4` adds an `embedding vector(1024)` pgvector
column (with an HNSW cosine index) that is **not** mapped as a JPA field — it is populated and
queried through native SQL by the embedding/retrieval services.

`RegulatoryKnowledgeService` performs **hybrid** retrieval over this table — a lexical (keyword)
arm plus a pgvector cosine arm over the `embedding` column, RRF-fused — to build the code-context
block passed to the AI provider and to resolve a free-form reference (e.g. `"LAMC 12.21-C"`) back
to its canonical URL. Without the embedding sidecar it degrades to lexical-only.

### ScreeningRule — `screening_rules`
A configurable pre-screening rule — the **primary** detection mechanism. Staff CRUD these
without code changes; when a rule's `conditionJson` matches a project's evaluation context,
it emits a `Finding`.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `code` | varchar, unique | e.g. `ZON-SETBACK-FRONT`, `FIRE-VHFHSZ` |
| `name` | varchar | Becomes the finding title |
| `category` | enum `FindingCategory` | |
| `severity` | enum `Severity` | |
| `conditionJson` | text (JSON) | Boolean condition tree — see `04-rule-engine.md` |
| `message` | text | Finding description; supports `{{placeholder}}` |
| `recommendation` | text | Supports `{{placeholder}}` |
| `codeReference` | varchar | e.g. `LAMC 12.08-C` |
| `codeUrl` | varchar | |
| `confidence` | int (0–100) | Baseline confidence for findings from this rule |
| `appliesToPermitTypes` | varchar (CSV) | Blank or `*` = all types |
| `priority` | int | Lower runs earlier (review-sequence ordering); indexed with `active` |
| `active` | boolean | |

### ClearanceRule — `clearance_rules`
A configurable clearance-identification rule. When its `conditionJson` matches, it emits a
`Clearance` naming the department, reason, confidence, and submittal requirements.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `code` | varchar, unique | e.g. `CLR-LAFD-LIFE-SAFETY` |
| `department` | enum `Department` | |
| `clearanceName` | varchar | |
| `conditionJson` | text (JSON) | Same grammar as screening rules |
| `reason` | text | Supports `{{placeholder}}` |
| `submittalRequirementsJson` | text (JSON string array) | Documents needed to obtain the clearance |
| `infoUrl` | varchar | Departmental info link |
| `confidence` | int (0–100) | Default 80 |
| `appliesToPermitTypes` | varchar (CSV) | |
| `priority` | int | Indexed with `active` |
| `active` | boolean | |

### Project — `projects`
The verified project record established at intake. Carries the UPID, permit type, resolved
parcel context, the applicant's dynamic-form answers, and the latest screening outcome.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `universalProjectId` | varchar, unique | `LA-<year>-<6 digits>` |
| `owner` | FK → User (lazy) | `ON DELETE CASCADE` |
| `title` | varchar | |
| `permitTypeCode` | varchar | References `PermitType.code` (by code, not FK) |
| `projectScope` | text | |
| `intendedUse` | varchar | |
| `description` | text | |
| `address` / `apn` | varchar | |
| `parcel` | FK → Parcel (lazy) | `ON DELETE SET NULL`; resolved at intake |
| `formDataJson` | text (JSON object) | Dynamic-form answers keyed by field id |
| `status` | enum `ProjectStatus` | Lifecycle |
| `currentReadinessScore` | int, nullable | Denormalized latest score |
| `currentReadinessStatus` | enum `ReadinessStatus` | Denormalized latest status |
| `usedAipPpc` | boolean (default true) | Feeds the ED19 assisted-vs-unassisted KPI |
| `submittedToEplanlaAt` | timestamptz, nullable | Set by the hand-off marker |
| `createdAt` / `updatedAt` | timestamptz | |

### Document — `documents`
An uploaded plan or supporting document. Files are validated for format/size and pass a
security scan before AI integration; a per-category version history is kept.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `project` | FK → Project (lazy) | `ON DELETE CASCADE` |
| `originalName` | varchar | |
| `contentType` | varchar | Browser-supplied MIME |
| `fileType` | varchar | Normalized: `PDF`, `DOCX`, `DXF`, `CAD`, `BIM`, `IMAGE`, `OTHER` |
| `sizeBytes` | bigint | |
| `storagePath` | varchar | Path relative to the storage base |
| `docCategory` | varchar, nullable | Required-doc key this file satisfies (drives completeness) |
| `scanStatus` | enum `ScanStatus` | |
| `scanDetail` | varchar | Human-readable scan result |
| `version` | int | Count of prior uploads of the same category + 1 |
| `extractedTextChars` | int | Characters of text extracted for the engine (0 = binary/scan-only) |
| `uploadedBy` | UUID, nullable | |
| `uploadedAt` | timestamptz | |

### PreCheckRun — `precheck_runs`
One execution of the pre-plan-check pipeline over a project's current documents and context.
A project may have many runs.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `project` | FK → Project (lazy) | `ON DELETE CASCADE` |
| `status` | enum `RunStatus` | `PENDING` → `PROCESSING` → `COMPLETED` / `FAILED` |
| `readinessScore` | int, nullable | 0–100 |
| `readinessStatus` | enum `ReadinessStatus` | |
| `summary` | text | Human summary incl. the advisory disclaimer |
| `findingCount` / `blockingCount` / `warningCount` / `infoCount` | int | Roll-ups |
| `clearanceCount` | int | |
| `processingMs` | bigint, nullable | For the 30-minute KPI |
| `aiProviderUsed` | varchar, nullable | `anthropic` or `heuristic` |
| `aiModelUsed` | varchar, nullable | e.g. the configured Claude model, or `keyword-v1` |
| `codeVersion` | varchar, nullable | Code edition basis stamped on the run |
| `triggeredBy` | enum `TriggeredBy` | `APPLICANT` \| `STAFF` \| `API` |
| `errorMessage` | text, nullable | On `FAILED` |
| `startedAt` / `completedAt` | timestamptz | |
| `createdAt` | timestamptz | |

### Finding — `findings`
A single issue surfaced by a run. Carries the standardized severity, category, code
reference + link, confidence score + bucket, triggering condition and assumptions, and a
plain-language recommendation. Optional page + bounding box drive the plan-viewer overlay.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `run` | FK → PreCheckRun (lazy) | `ON DELETE CASCADE` |
| `category` | enum `FindingCategory` | |
| `severity` | enum `Severity` | |
| `title` | varchar | |
| `description` | text | Rendered from the rule/AI message |
| `codeReference` / `codeUrl` | varchar | Cited section + link |
| `confidence` | int (0–100) | |
| `confidenceLevel` | enum `ConfidenceLevel` | Bucketed from `confidence` in the builder |
| `triggeringCondition` | text | Why it fired |
| `assumptions` | text | Any assumptions made |
| `recommendation` | text | Actionable next step |
| `source` | enum `FindingSource` | `RULE` \| `AI` \| `COMPLETENESS` |
| `ruleCode` | varchar | Originating rule code, `AI`, or `COMPLETENESS-*` |
| `pageNumber`, `locationX/Y/Width/Height` | int/double, nullable | Visual overlay coords |
| `staffDisposition` | enum `StaffDisposition` | Human-in-the-loop review state; defaults `PENDING` |
| `staffComment` | text, nullable | |
| `staffReviewedBy` | UUID, nullable | |
| `staffReviewedAt` | timestamptz, nullable | |
| `applicantFlagged` | boolean | Applicant "this flag is inaccurate" notice |
| `applicantFlagComment` | text, nullable | |
| `createdAt` | timestamptz | |

The builder's `.confidence(v)` setter also derives `confidenceLevel` via
`ConfidenceLevel.fromScore(v)`, so the numeric score and the coarse bucket never drift.

### Clearance — `clearances`
A likely-required departmental clearance identified for a run.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `run` | FK → PreCheckRun (lazy) | `ON DELETE CASCADE` |
| `department` | enum `Department` | |
| `clearanceName` | varchar | |
| `reason` | text | Why it is triggered |
| `confidence` | int (0–100) | Default 80 |
| `confidenceLevel` | enum `ConfidenceLevel` | Derived from `confidence` |
| `submittalRequirementsJson` | text (JSON array) | Documents needed |
| `infoUrl` | varchar | |
| `source` | enum `FindingSource` | `RULE` (AI-augmented clearances possible by design) |
| `ruleCode` | varchar | |
| `staffDisposition` | enum `StaffDisposition` | Defaults `PENDING` |
| `staffComment` / `staffReviewedBy` / `staffReviewedAt` | | Staff QA |
| `createdAt` | timestamptz | |

### FeedbackEntry — `feedback_entries`
The auditable inbox for continuous improvement. Sources: applicants flagging an inaccurate
finding, and staff logging missed detections or rule-tuning notes.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `runId` / `findingId` / `clearanceId` | UUID, nullable | What the feedback references |
| `submittedBy` | UUID, nullable | |
| `submitterRole` | varchar, nullable | |
| `type` | varchar | `INACCURATE_FLAG` \| `MISSED_DETECTION` \| `RULE_TUNING` \| `GENERAL` |
| `comment` | text | |
| `status` | varchar | `OPEN` \| `REVIEWED` \| `APPLIED` \| `DISMISSED` (default `OPEN`) |
| `createdAt` | timestamptz | |

`type` and `status` are string-typed (not Java enums) — the small, evolving vocabularies are
documented inline on the entity rather than fixed by an enum class.

### AuditLog — `audit_log`
Append-only audit trail. Every meaningful action writes one row.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `actorType` | varchar | `USER` \| `STAFF` \| `API_CLIENT` \| `SYSTEM` |
| `actorId` / `actorLabel` | varchar, nullable | Identity + display label |
| `action` | varchar | e.g. `PROJECT_CREATED`, `DOCUMENT_UPLOADED`, `SCREENING_TRIGGERED`, `SCREENING_COMPLETED` |
| `entityType` / `entityId` | varchar, nullable | Target of the action |
| `detail` | text, nullable | |
| `ipAddress` | varchar, nullable | |
| `beforeJson` / `afterJson` | text, nullable | Before/after entity snapshots (added in `V5`) for field-level change forensics on rule / knowledgebase / user mutations |
| `createdAt` | timestamptz (indexed desc) | |

Written via `AuditService`, whose writes never propagate failures — auditing must not break
the action being audited. There is also an `(entity_type, entity_id)` index for
per-entity history.

---

## Enums (`model/enums/`)

| Enum | Values | Purpose |
|---|---|---|
| `User.Role` | `APPLICANT`, `STAFF`, `ADMIN` | RBAC (nested on `User`) |
| `PermitCategory` | `RESIDENTIAL`, `MULTI_FAMILY`, `COMMERCIAL`, `OTHER` | Broad project class; multi-family & commercial are ED19 priorities |
| `Jurisdiction` | `CITY_LA`, `COUNTY_LA`, `STATE_CA`, `FEDERAL` | Source of a regulatory code entry |
| `Department` | `LADBS`, `CITY_PLANNING`, `LAFD`, `BOE`, `BOS`, `BSS`, `BCA`, `LAHD`, `LADWP`, `DOT`, `OTHER` | Clearance-issuing departments |
| `FindingCategory` | `COMPLETENESS`, `ZONING`, `BUILDING`, `STRUCTURAL`, `ACCESSIBILITY`, `FIRE`, `MECHANICAL`, `ELECTRICAL`, `PLUMBING`, `GREEN`, `GENERAL` | Discipline of a finding |
| `Severity` | `BLOCKING`, `WARNING`, `INFORMATIONAL` | Standardized severity |
| `ConfidenceLevel` | `HIGH` (≥85), `MEDIUM` (≥55), `LOW` (else) | Coarse bucket via `fromScore(int)` |
| `FindingSource` | `RULE`, `AI`, `COMPLETENESS` | Which engine produced the finding |
| `ProjectStatus` | `DRAFT`, `INTAKE`, `SCREENING`, `SCREENED`, `SUBMITTED_TO_EPLANLA`, `ARCHIVED` | Project lifecycle (advisory only — never issues permits) |
| `ReadinessStatus` | `READY_FOR_SUBMISSION`, `REQUIRES_ATTENTION`, `INCOMPLETE`, `NOT_ASSESSED` | Applicant-facing readiness notification |
| `RunStatus` | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` | Screening run lifecycle |
| `ScanStatus` | `PENDING`, `PASSED`, `FAILED`, `QUARANTINED` | Security-scan state of an upload |
| `StaffDisposition` | `PENDING`, `ACCEPTED`, `MODIFIED`, `REJECTED` | Human-in-the-loop review state |
| `TriggeredBy` | `APPLICANT`, `STAFF`, `API` | Who initiated a run (intake-pathway KPI) |

`ConfidenceLevel.fromScore(int)` thresholds: `>= 85 → HIGH`, `>= 55 → MEDIUM`, else `LOW`.
Findings default to `HIGH`; clearances default to `MEDIUM`, both re-derived from the numeric
confidence in the builder.
