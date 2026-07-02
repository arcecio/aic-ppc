# 06 — API Reference

City of Los Angeles / LADBS — **AI-Powered Pre-Plan Check Assistant** (RFP 2025AIP007)

Two API surfaces:

1. **Web app API** (`/api/auth`, `/api/projects`, `/api/runs`, `/api/reference`, `/api/staff`,
   `/api/admin`) — used by the React frontend, authenticated with a **Bearer JWT**. These
   endpoints are **implemented** and verified against the controllers.
2. **Integration API** (`/api/v1/*`) and the **MCP tool endpoint** (`/api/mcp`) — for ePlanLA,
   LACPS, and other City systems, authenticated with **`X-API-Key`**. These are **implemented**
   (`IntegrationApiController`, `McpController`); the async/webhook plumbing fires on
   API-triggered runs. The far side — live ePlanLA/LACPS integration — remains designed.

Live, always-current documentation is published via **springdoc / Swagger UI** at
`/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`). Both paths are permit-all.

Base URL in local Docker: `http://localhost:8082`. All request/response bodies are JSON unless
noted; timestamps are ISO-8601 (`Instant`).

---

## Conventions

- **Auth (web app):** `Authorization: Bearer <jwt>`. Obtain a token from `/api/auth/login` or
  `/api/auth/register`.
- **Auth (integration):** `X-API-Key: <raw key>`. Keys are issued by an admin; only the
  SHA-256 hash is stored server-side.
- **Errors:** `GlobalExceptionHandler` maps `ApiException` to its HTTP status with a message,
  and bean-validation failures to `400`. `application.yml` sets `include-message: always` and
  `include-binding-errors: always`.
- **Access control:** applicant endpoints enforce ownership in the service layer — the owner
  or any staff/admin may access a project; everyone else gets `403`.

---

## Authentication endpoints — `/api/auth` (AuthController)

| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| POST | `/api/auth/register` | none | `RegisterRequest` | `AuthResponse` |
| POST | `/api/auth/login` | none | `LoginRequest` | `AuthResponse` |
| GET | `/api/auth/me` | JWT | — | `UserDto` |
| PATCH | `/api/auth/profile` | JWT | `UpdateProfileRequest` | `AuthResponse` (new token) |
| POST | `/api/auth/change-password` | JWT | `ChangePasswordRequest` | `204 No Content` |

`RegisterRequest`: `{ email (email), password (≥8 chars), name, organization? }`. New users get
role `APPLICANT`. `LoginRequest`: `{ email, password }`.

**Example — register**
```http
POST /api/auth/register
Content-Type: application/json

{ "email": "arch@example.com", "password": "hunter2secret", "name": "A. Architect",
  "organization": "Example Architects" }
```
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": { "id": "…", "email": "arch@example.com", "name": "A. Architect",
            "role": "APPLICANT", "organization": "Example Architects" }
}
```

---

## Applicant endpoints — `/api/projects` (ProjectController)

All require a JWT. `{id}` is a project UUID.

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/projects` | `CreateProjectRequest` | `ProjectDto` |
| GET | `/api/projects` | — | `ProjectSummaryDto[]` (owner's projects) |
| GET | `/api/projects/{id}` | — | `ProjectDto` |
| PATCH | `/api/projects/{id}` | `UpdateProjectRequest` | `ProjectDto` |
| POST | `/api/projects/{id}/documents` | multipart `file` + optional `docCategory` | `DocumentDto` |
| GET | `/api/projects/{id}/documents` | — | `DocumentDto[]` |
| GET | `/api/projects/{id}/documents/{docId}/download` | — | file stream (octet-stream) |
| DELETE | `/api/projects/{id}/documents/{docId}` | — | `204 No Content` |
| POST | `/api/projects/{id}/screen` | — | `RunDto` (PENDING) |
| GET | `/api/projects/{id}/runs` | — | `RunDto[]` (newest first) |
| GET | `/api/projects/{id}/runs/latest` | — | `RunDetailDto` or `204` if none |
| POST | `/api/projects/{id}/submit-eplanla` | — | `ProjectDto` (marks hand-off) |
| GET | `/api/projects/{id}/report` | — | PDF (`application/pdf`) |

`CreateProjectRequest`: `{ title, permitTypeCode, projectScope?, intendedUse?, description?,
address?, apn?, formData? }`. `formData` is the dynamic-form answer object keyed by the permit
type's field ids. On create, the parcel is resolved from `apn`/`address`, a Universal Project ID
is assigned, and status becomes `INTAKE`.

**Example — create a project**
```http
POST /api/projects
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "title": "New ADU at rear yard",
  "permitTypeCode": "ADU",
  "projectScope": "Detached 700 sq ft accessory dwelling unit",
  "address": "123 Example St, Los Angeles, CA",
  "formData": { "aduType": "Detached", "squareFootage": 700, "valuation": 180000 }
}
```
```json
{
  "id": "…", "universalProjectId": "LA-2026-004821",
  "title": "New ADU at rear yard", "permitTypeCode": "ADU",
  "status": "INTAKE", "currentReadinessStatus": "NOT_ASSESSED",
  "parcel": { "apn": "…", "zone": "R1", "overlays": ["Hillside"], "hazardZones": [] },
  "formData": { "aduType": "Detached", "squareFootage": 700, "valuation": 180000 },
  "documents": []
}
```

**Example — upload a plan** (`multipart/form-data`)
```http
POST /api/projects/{id}/documents
Authorization: Bearer <jwt>

file=<plan.pdf>  &  docCategory=architectural_plans
```
```json
{
  "id": "…", "originalName": "plan.pdf", "fileType": "PDF", "sizeBytes": 542118,
  "docCategory": "architectural_plans", "scanStatus": "PASSED",
  "scanDetail": "Passed format, size, and malware-signature checks.",
  "version": 1, "extractedTextChars": 0, "uploadedAt": "2026-07-01T18:20:00Z"
}
```
The security scan runs at upload: unaccepted extension or oversize → `scanStatus: FAILED`; a
matched malware signature → `QUARANTINED`; otherwise `PASSED`. Accepted extensions: pdf, docx,
doc, dxf, dwg, rvt, ifc, png, jpg, jpeg.

**Example — trigger a screening run**
```http
POST /api/projects/{id}/screen
Authorization: Bearer <jwt>
```
```json
{ "id": "run-uuid", "projectId": "…", "universalProjectId": "LA-2026-004821",
  "status": "PENDING", "triggeredBy": "APPLICANT", "createdAt": "2026-07-01T18:25:00Z" }
```
Trigger returns immediately with a `PENDING` run; poll `/runs/latest` (or a specific run) until
`status` is `COMPLETED` or `FAILED`. `triggeredBy` is `STAFF` when a staff user triggers it.

**Example — latest run detail** (`RunDetailDto` = run + findings + clearances)
```json
{
  "run": {
    "id": "run-uuid", "status": "COMPLETED",
    "readinessScore": 71, "readinessStatus": "REQUIRES_ATTENTION",
    "summary": "Submission readiness: Requires Attention (score 71/100). 0 blocking, 2 warning finding(s); 1 likely clearance(s) identified. … Results are advisory only …",
    "findingCount": 3, "blockingCount": 0, "warningCount": 2, "infoCount": 1,
    "clearanceCount": 1, "processingMs": 842,
    "aiProviderUsed": "heuristic", "aiModelUsed": "keyword-v1",
    "codeVersion": "LAMC/Title24 2024 seed", "triggeredBy": "APPLICANT",
    "completedAt": "2026-07-01T18:25:01Z"
  },
  "findings": [
    { "id": "…", "category": "ZONING", "severity": "WARNING",
      "title": "Front yard setback below minimum",
      "description": "The proposed front yard setback appears to be 10 ft, but R1 zones (R1) generally require a 15 ft front yard (LAMC 12.08-C).",
      "codeReference": "LAMC 12.08-C", "codeUrl": "https://codelibrary.amlegal.com/codes/los_angeles/",
      "confidence": 80, "confidenceLevel": "MEDIUM",
      "triggeringCondition": "Rule ZON-SETBACK-FRONT matched the submission.",
      "recommendation": "Increase the front yard setback to at least 15 ft, or …",
      "source": "RULE", "ruleCode": "ZON-SETBACK-FRONT",
      "staffDisposition": "PENDING", "applicantFlagged": false }
  ],
  "clearances": [
    { "id": "…", "department": "LADBS", "clearanceName": "LADBS — Grading / Geology Clearance",
      "reason": "Hillside conditions … require LADBS Grading Division review …",
      "confidence": 74, "confidenceLevel": "MEDIUM",
      "submittalRequirements": ["Grading plan with cut/fill quantities and drainage", "Geotechnical / soils and geology report"],
      "infoUrl": "https://www.ladbs.org/", "source": "RULE",
      "ruleCode": "CLR-LADBS-GRADING", "staffDisposition": "PENDING" }
  ]
}
```
Findings are sorted BLOCKING → WARNING → INFORMATIONAL, then by descending confidence.

**PDF report** — `GET /api/projects/{id}/report` streams the compliance report for the latest
run (`Content-Disposition: attachment; filename="AIP-PPC-<UPID>.pdf"`). The report leads with
the advisory-only disclaimer, then readiness status/score, findings, likely clearances, and
status-specific next steps.

---

## Run & feedback endpoints — `/api/runs` (RunController)

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/runs/{runId}` | — | `RunDetailDto` (access-controlled) |
| POST | `/api/runs/findings/{findingId}/flag` | `{ comment }` (required) | `FindingDto` (now `applicantFlagged: true`) |
| POST | `/api/runs/feedback` | `{ type?, comment?, runId?, findingId? }` | `{ id, status }` |

The flag endpoint implements the applicant's "notify City staff of an inaccurate flag"
capability (SOW §2.2.4). Feedback is captured in the `feedback_entries` inbox for continuous
improvement (SOW §2.2.13).

---

## Reference endpoints — `/api/reference` (ReferenceController)

Read-only intake reference data. Require a JWT (any authenticated user).

| Method | Path | Query | Returns |
|---|---|---|---|
| GET | `/api/reference/permit-types` | — | `PermitTypeDto[]` (active, ordered by name) |
| GET | `/api/reference/permit-types/{code}` | — | `PermitTypeDto` |
| GET | `/api/reference/parcels` | `q` | `ParcelDto[]` (address/APN search) |
| GET | `/api/reference/codes` | `q` | `CodeResult[]` (regulatory-code search) |

`PermitTypeDto` includes the raw `formSchema` and `requiredDocs` JSON so the UI can render the
correct dynamic intake form. `CodeResult`:
`{ externalId, jurisdiction, codeType, section, title, summary, url, version }`.

---

## Integration API — `/api/v1/*` (IntegrationApiController — implemented)

Authenticated with `X-API-Key`; a valid key grants `ROLE_API_CLIENT`. This is the surface
ePlanLA/LACPS use for **headless** pre-screening (SOW §1.2.3, §2.2.14; Appendix 3 §2.x, use
cases 3.1/3.3/3.5). It mirrors the applicant flow but is keyed by an `ApiClient` rather than a
user, and screening runs are attributed `triggeredBy: API`.

Contract (verified against `IntegrationApiController` / `IntegrationService`):

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/projects` | Submit a project: `CreateProjectRequest` (JSON) → `ProjectDto` with its Universal Project ID |
| GET | `/api/v1/projects/{universalProjectId}` | Fetch a project by its Universal Project ID → `ProjectDto` |
| POST | `/api/v1/projects/{projectId}/documents` | Upload a plan/supporting document (multipart `file` + optional `docCategory`) → `DocumentDto` |
| POST | `/api/v1/projects/{projectId}/screen` | Trigger a **headless** screening run; returns `{ runId, projectId, status, statusUrl, resultsUrl }` immediately |
| POST | `/api/v1/screenings` | **One-call** submit + screen: `CreateProjectRequest` → `{ universalProjectId, projectId, runId, status, statusUrl, resultsUrl }` |
| GET | `/api/v1/runs/{runId}` | Poll run status as `RunDto` (PENDING / PROCESSING / COMPLETED / FAILED) |
| GET | `/api/v1/runs/{runId}/results` | Retrieve structured results as `RunDetailDto` (issues, completeness findings, clearances, confidence/score, timestamps, code references) |

The JSON result shape reuses `RunDetailDto` (run summary + findings + clearances), satisfying
"structured results in JSON … including identified issues, completeness findings, potential
clearance requirements, confidence/score indicators, timestamps, and reference information"
(Appendix 3 §5.1.1 / technical req 17).

**Example — one-call submit + screen headless**
```http
POST /api/v1/screenings
X-API-Key: <raw key>
Content-Type: application/json

{ "title": "Ground-floor retail TI", "permitTypeCode": "COMMERCIAL_TI",
  "address": "500 Example Blvd", "formData": { "occupancyGroup": "M", "squareFootage": 4200 } }
```
```json
{ "universalProjectId": "LA-2026-004821", "projectId": "…", "runId": "run-uuid",
  "status": "PENDING", "statusUrl": "/api/v1/runs/run-uuid",
  "resultsUrl": "/api/v1/runs/run-uuid/results" }
```
Then poll `GET /api/v1/runs/{runId}` until `status` is `COMPLETED`/`FAILED`, and fetch
`GET /api/v1/runs/{runId}/results` for the full `RunDetailDto`. (The two-step
`POST /api/v1/projects` then `POST /api/v1/projects/{projectId}/screen` path returns the same
`{ runId, status, statusUrl, resultsUrl }` shape.)

### Webhooks (`ScreeningWebhookNotifier`)

When an **API-triggered** run finishes, the backend POSTs a `screening.completed` callback to
each active `ApiClient` that has a `webhookUrl` — event-driven notification instead of polling
(SOW §2.2.14; Appendix 3 §2.1.4). Delivery is best-effort, asynchronous, and never propagates
failures into the screening path. Payload (as built by the notifier):

```json
{
  "event": "screening.completed",
  "runId": "…", "projectId": "…", "universalProjectId": "LA-2026-004821",
  "status": "COMPLETED", "readinessStatus": "REQUIRES_ATTENTION", "readinessScore": 71,
  "findingCount": 3, "clearanceCount": 1
}
```
The receiver then fetches full results via `GET /api/v1/runs/{runId}/results`.

---

## MCP tool endpoint — `/api/mcp` (McpController — implemented)

A single `POST /api/mcp` endpoint speaking **JSON-RPC 2.0**, accessible to `API_CLIENT`,
`STAFF`, or `ADMIN` (per `SecurityConfig`). This exposes the pre-plan-check engine as a **Model
Context Protocol** tool surface so external AI models and City systems can securely invoke it as
a standardized tool (SOW §1.1 objective — "Implement Model Context Protocol (MCP) to provide a
universal, standardized way for AI models to securely connect to external tools, databases, and
data sources"; Appendix 3 technical req 1).

JSON-RPC methods: `initialize` (returns `protocolVersion 2024-11-05` + `serverInfo`),
`tools/list` (advertises the tools below with their input schemas), and `tools/call` (invokes a
tool by name, returning `{ "content": [{ "type": "text", "text": … }] }`).

Tools (verified against `McpController`):

| Tool | Args | Purpose |
|---|---|---|
| `lookup_parcel` | `query` | Resolve an address/APN to zoning, overlays, and hazard zones |
| `list_permit_types` | — | List available permit types and categories |
| `identify_permit` | `description` | Suggest a likely permit type from a plain-language project description |
| `search_codes` | `query` | Query the LAMC / Title 24 / clearance knowledgebase |

MCP calls are authenticated by the same `X-API-Key` mechanism and audited like every other
transaction.

---

## Staff endpoints — `/api/staff` (StaffController — implemented)

Require a JWT for a `STAFF` or `ADMIN` user (matcher in `SecurityConfig`). This is the Staff
Review & Analytics surface (SOW §1.2.2, §2.2.5, §2.2.12; Appendix 3 §6.1.2–6.1.4, use case 2.1).

| Method | Path | Body / Query | Returns |
|---|---|---|---|
| GET | `/api/staff/analytics` | — | `AnalyticsDto` (KPI rollup) |
| GET | `/api/staff/projects` | — | `ProjectSummaryDto[]` (all projects) |
| GET | `/api/staff/runs` | `status?`, `page?`, `size?` | `RunDto[]` |
| GET | `/api/staff/runs/{runId}` | — | `RunDetailDto` |
| POST | `/api/staff/findings/{findingId}/review` | `DispositionRequest` | `FindingDto` (updated disposition) |
| POST | `/api/staff/clearances/{clearanceId}/review` | `DispositionRequest` | disposition result |
| GET | `/api/staff/feedback` | `status?` | feedback entries |
| PATCH | `/api/staff/feedback/{id}` | `{ status }` | updated feedback entry |

`DispositionRequest`: `{ disposition (required, e.g. ACCEPTED/MODIFIED/REJECTED), comment? }` —
the human-in-the-loop accept/modify/reject on each AI or rule finding/clearance (SOW §4.1.3;
Appendix 3 §5.1.5). `AnalyticsDto` fields: `totalProjects, projectsUsingAipPpc, totalRuns,
completedRuns, failedRuns, avgProcessingMs, pctWithinTarget, targetProcessingMs, totalFindings,
totalClearances, avgReadinessScore, readyForSubmission, requiresAttention, incomplete,
submittedToEplanla, openFeedback`. PowerBI/SAP export of these KPIs remains designed.

---

## Admin endpoints — `/api/admin` (AdminController — implemented)

Require a JWT for an `ADMIN` user. This is the **configurable rule engine** and system-admin
surface — City staff update rules, thresholds, and references without vendor code changes
(SOW §1.2.1, §2.1.6, §5.1.6; Appendix 3 §1.2.1, use case 2.3).

| Method | Path | Body / Query | Returns |
|---|---|---|---|
| GET | `/api/admin/screening-rules` | — | `ScreeningRuleDto[]` |
| POST | `/api/admin/screening-rules` | `ScreeningRuleRequest` | `ScreeningRuleDto` |
| PUT | `/api/admin/screening-rules/{id}` | `ScreeningRuleRequest` | `ScreeningRuleDto` |
| DELETE | `/api/admin/screening-rules/{id}` | — | `204 No Content` |
| GET | `/api/admin/clearance-rules` | — | `ClearanceRuleDto[]` |
| POST | `/api/admin/clearance-rules` | `ClearanceRuleRequest` | `ClearanceRuleDto` |
| PUT | `/api/admin/clearance-rules/{id}` | `ClearanceRuleRequest` | `ClearanceRuleDto` |
| DELETE | `/api/admin/clearance-rules/{id}` | — | `204 No Content` |
| GET | `/api/admin/api-clients` | — | `ApiClientDto[]` |
| POST | `/api/admin/api-clients` | `CreateApiClientRequest` | `ApiClientCreatedDto` (raw key shown once) |
| DELETE | `/api/admin/api-clients/{id}` | — | `204 No Content` (revoke) |
| GET | `/api/admin/users` | — | `UserDto[]` |
| PATCH | `/api/admin/users/{id}/role` | `{ role }` | `UserDto` |
| PATCH | `/api/admin/users/{id}/enabled` | `{ enabled }` | `UserDto` |
| GET | `/api/admin/audit` | `page?`, `size?` | audit-log entries (newest first) |

Creating an API client returns the raw `X-API-Key` **once** (`aip_live_<40 hex>`); only its
SHA-256 hash is persisted (`ApiKeyHasher`). Rule create/update/delete and client/user changes are
recorded in the append-only `audit_log`.

### Knowledgebase — `/api/admin/regulatory-codes` (AdminKnowledgeController — implemented)

The update path for the regulatory corpus (SOW §2.1.6, §2.2.13). All entries are
**upserted by `externalId`** — amendments update sections in place, never duplicate.
Every write is audited.

| Method | Path | Body / params | Returns |
|---|---|---|---|
| GET | `/api/admin/regulatory-codes` | `q?` (matches externalId/title/summary/tags/section) | `RegulatoryCodeDto[]` |
| POST | `/api/admin/regulatory-codes` | `RegulatoryCodeRequest` | created entry (409 if `externalId` exists) |
| PUT | `/api/admin/regulatory-codes/{id}` | `RegulatoryCodeRequest` (`externalId` immutable) | updated entry |
| DELETE | `/api/admin/regulatory-codes/{id}` | — | `204 No Content` |
| POST | `/api/admin/regulatory-codes/import` | `RegulatoryCodeRequest[]` — bulk corpus drop | `{inserted, updated, unchanged, embedded, total}` |
| POST | `/api/admin/regulatory-codes/sync` | — (re-sync from the bundled release corpus) | same sync-result shape |
| GET | `/api/admin/regulatory-codes/status` | — | `{totalEntries, embeddedEntries, embeddingProvider, embeddingAvailable, schedulerEnabled, schedulerCron}` |

`RegulatoryCodeRequest`: `{externalId*, jurisdiction, codeType, section, title*, summary, url, tags, version}`.

A monthly scheduler (`KnowledgeRefreshScheduler`, `KB_SCHEDULER_ENABLED` / `KB_SCHEDULER_CRON`,
default 04:00 on the 1st) re-runs sync + embedding backfill automatically (SOW §2.2.13). The
backfill indexes sections into the pgvector `embedding` column via the optional TEI sidecar;
knowledgebase retrieval degrades to lexical-only when the sidecar is absent.

---

## DTO field reference

- **`ProjectDto`**: `id, universalProjectId, title, permitTypeCode, projectScope, intendedUse,
  description, address, apn, parcel(ParcelDto), formData(map), status, currentReadinessScore,
  currentReadinessStatus, usedAipPpc, ownerName, ownerEmail, submittedToEplanlaAt, createdAt,
  updatedAt, documents(DocumentDto[])`.
- **`ProjectSummaryDto`**: `id, universalProjectId, title, permitTypeCode, address, status,
  currentReadinessScore, currentReadinessStatus, ownerName, createdAt, updatedAt`.
- **`DocumentDto`**: `id, originalName, fileType, sizeBytes, docCategory, scanStatus,
  scanDetail, version, extractedTextChars, uploadedAt`.
- **`ParcelDto`**: `id, apn, address, zone, generalPlanLandUse, overlays[], hazardZones[],
  councilDistrict, communityPlanArea, latitude, longitude`.
- **`RunDto`**: `id, projectId, universalProjectId, status, readinessScore, readinessStatus,
  summary, findingCount, blockingCount, warningCount, infoCount, clearanceCount, processingMs,
  aiProviderUsed, aiModelUsed, codeVersion, triggeredBy, errorMessage, startedAt, completedAt,
  createdAt`.
- **`FindingDto`**: `id, category, severity, title, description, codeReference, codeUrl,
  confidence, confidenceLevel, triggeringCondition, assumptions, recommendation, source,
  ruleCode, pageNumber, locationX/Y/Width/Height, staffDisposition, staffComment,
  applicantFlagged, applicantFlagComment, createdAt`.
- **`ClearanceDto`**: `id, department, clearanceName, reason, confidence, confidenceLevel,
  submittalRequirements[], infoUrl, source, ruleCode, staffDisposition, staffComment,
  createdAt`.
- **`UserDto`** / **`AuthResponse`**: `AuthResponse` = `{ token, user(UserDto) }`.
