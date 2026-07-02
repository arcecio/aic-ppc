# 02 — Architecture

City of Los Angeles / LADBS — **AI-Powered Pre-Plan Check Assistant** (RFP 2025AIP007)

This document describes the system's runtime shape: the component topology, the backend
service map (controllers → services → repositories), the asynchronous screening pipeline,
the security model, deployment, and configuration. It mirrors the Blue reference architecture
(Spring Boot + React + Postgres on Docker/Colima).

---

## Component diagram

```
                              ┌──────────────────────────────────────────────┐
   External applicants        │       React + Vite + TS frontend (impl.)     │
   (Angeleno Account /  ─────▶│  applicant intake · results · staff review · │  :8095 (nginx)
    staff Okta SSO)           │  analytics · admin rules · TanStack Query ·  │
                              │  Tailwind (WCAG/ADA: skip link, focus ring)  │
                              └───────────────────────┬──────────────────────┘
                                                      │ JSON over HTTPS (Bearer JWT)
                                                      ▼
   City systems               ┌──────────────────────────────────────────────┐
   (ePlanLA / LACPS)   ──────▶│           Spring Boot backend (Java 21)      │  :8082 → 8080
   X-API-Key + webhooks       │                                              │
                              │  Security filters: ApiKeyAuthFilter,         │
                              │                    JwtAuthFilter              │
                              │  Controllers → Services → Repositories       │
                              │  Async screening pipeline (thread pool)      │
                              │  AI providers: Anthropic | Heuristic         │
                              │  Doc extraction: PDFBox / POI                │
                              │  Report: PDFBox                              │
                              └───────┬──────────────────────┬───────────────┘
                                      │ JDBC/JPA (Flyway)     │ HTTPS (outbound)
                                      ▼                       ▼
                        ┌──────────────────────┐   ┌────────────────────────┐
                        │   PostgreSQL 16      │   │  Anthropic Messages API │
                        │   (aipppc)           │   │  (optional; augments)   │
                        │   :5434 → 5432       │   └────────────────────────┘
                        └──────────────────────┘
                        ┌──────────────────────┐
                        │  Local file storage  │  (uploaded plans; ./storage volume)
                        └──────────────────────┘
```

Everything is containerized and runs on a single Colima VM. Ports are offset from the
Blue/munch stacks so all can coexist.

---

## Backend service map

Package root: `com.lacity.aipppc` under `backend/src/main/java/`.

### Controllers (`controller/`)
| Controller | Base path | Responsibility |
|---|---|---|
| `AuthController` | `/api/auth` | register / login / me / profile / change-password (issues JWT) |
| `ProjectController` | `/api/projects` | applicant project lifecycle: create/list/get/update, document upload/list/download/delete, trigger screening, list runs, latest run, PDF report, ePlanLA hand-off |
| `RunController` | `/api/runs` | run detail, flag inaccurate finding, submit feedback |
| `ReferenceController` | `/api/reference` | read-only reference data: permit types, parcel search, code search |
| `StaffController` | `/api/staff` | Staff Review & Analytics: analytics/KPIs, project & run browsing, finding/clearance review (accept/modify/reject), feedback inbox (STAFF/ADMIN) |
| `AdminController` | `/api/admin` | configurable rule engine CRUD (`screening-rules`, `clearance-rules`), API-client management, user role/enabled management, audit-log query (ADMIN) |
| `IntegrationApiController` | `/api/v1` | headless integration API: submit project, upload docs, screen, one-call `screenings`, poll run status + retrieve results (X-API-Key / `ROLE_API_CLIENT`) |
| `McpController` | `/api/mcp` | JSON-RPC 2.0 MCP tool endpoint (`initialize`, `tools/list`, `tools/call`) |
| `GlobalExceptionHandler` | (advice) | maps `ApiException` and validation errors to HTTP responses |

All of the above are **implemented**; their security matchers are defined in `SecurityConfig`
(see the RBAC table below). See `06-api.md` for the full endpoint contracts.

### Services (`service/`)
| Service | Responsibility |
|---|---|
| `UserService` | registration, login, JWT issuance, profile, password change |
| `ProjectService` | intake (UPID generation + parcel resolution), form persistence, document upload (with security scan), ePlanLA hand-off marker, DTO mapping |
| `ParcelService` | parcel/GIS lookup by APN or normalized address |
| `PreCheckService` | trigger a run (create PENDING, dispatch async), list/latest runs, assemble `RunDetailDto` |
| `StorageService` | persist uploads, run the pre-AI security scan, extract text (PDFBox/POI/DXF) |
| `ReportService` | generate the exportable PDF compliance report (PDFBox) |
| `AuditService` | append-only audit-log writer (never propagates failures) |
| `FeedbackService` | applicant flag + staff feedback inbox (list/update status) |
| `StaffService` | staff project/run browsing and finding/clearance review (disposition) |
| `AnalyticsService` | computes the `AnalyticsDto` KPI rollup for the staff dashboard |
| `RuleAdminService` | CRUD for `screening_rules` / `clearance_rules` (configurable rule engine, no vendor code) |
| `AdminUserService` | admin user list + role/enabled management |
| `ApiClientService` | issue/list/revoke integration API clients (raw key shown once) |
| `IntegrationService` | orchestrates the `/api/v1` headless flow (submit, screen, poll, results) |
| `JsonUtil` | JSON helpers (read tree, to map, to string list) |
| `AdminBootstrapRunner` | idempotently promotes a configured email to ADMIN on boot |
| `ReferenceDataSeeder` | boot-time seeding: add-only for staff-editable config (permit types, parcels, rule packs); delegates the code corpus to `KnowledgeSyncService` (upsert) |
| **`service/knowledge/`** | |
| `KnowledgeSyncService` | corpus ingestion, **upsert by `external_id`** (classpath corpus + admin import), audited |
| `KnowledgeIndexService` | embedding backfill for sections without a vector (batched, no-op offline) |
| `KnowledgeRefreshScheduler` | monthly `@Scheduled` re-sync + re-embed (SOW 2.2.13), `KB_SCHEDULER_ENABLED`-gated |
| **`service/embedding/`** | |
| `EmbeddingProvider` / `TeiEmbeddingProvider` | TEI sidecar client (e5-large-v2, `passage:`/`query:` prefixes, cool-down on failure) |
| `EmbeddingService` | failure-tolerant facade; pgvector literal helper |
| `RrfFusion` (`service/rag/`) | Reciprocal Rank Fusion (k=60) of the lexical + vector rankings |
| **`service/screening/`** | |
| `ScreeningService` | the async pipeline orchestrator (see below) |
| `ProjectContextBuilder` | flattens project/parcel/form/docs into the rule context |
| `CompletenessService` | required-document + submission-issue validation |
| `RuleConditionEvaluator` (`service/rules/`) | evaluates the JSON condition grammar |
| `RegulatoryKnowledgeService` | **hybrid retrieval** over the code knowledgebase: lexical + pgvector cosine arms, RRF-fused, degrades to lexical-only without the sidecar; reference→URL |
| `TemplateRenderer` | `{{placeholder}}` substitution in messages |
| `ScreeningWebhookNotifier` | fires `screening.completed` webhooks for API-triggered runs |
| **`service/ai/`** | |
| `AiAnalysisService` | selects the active AI provider, with heuristic fallback |
| `AiProvider` | the pluggable provider interface |
| `AnthropicAiProvider` | Claude Messages-API implementation |
| `HeuristicAiProvider` | deterministic offline keyword provider (always available) |
| `AiModels` | request/analysis/finding DTOs shared by the providers |

### Repositories (`repository/`)
Spring Data JPA interfaces, one per aggregate: `UserRepository`, `ApiClientRepository`,
`PermitTypeRepository`, `ParcelRepository`, `RegulatoryCodeRepository`,
`ScreeningRuleRepository`, `ClearanceRuleRepository`, `ProjectRepository`,
`DocumentRepository`, `PreCheckRunRepository`, `FindingRepository`, `ClearanceRepository`,
`FeedbackEntryRepository`, `AuditLogRepository`. Notable finders:
`ScreeningRuleRepository.findByActiveTrueOrderByPriorityAsc()`,
`PreCheckRunRepository.findTopByProjectOrderByCreatedAtDesc()`,
`ApiClientRepository.findByKeyHashAndActiveTrue()`.

### Config (`config/`)
`SecurityConfig` (filter chain + RBAC), `AsyncConfig` (screening thread pool),
`CorsConfig`, `JacksonConfig`, `OpenApiConfig` (Swagger descriptor).

### Frontend (`frontend/src/`) — implemented
React 18 + Vite 5 + TypeScript, TanStack Query, Tailwind (WCAG/ADA). Pages (`pages/`):
`Login`, `Register`, `Dashboard`, `NewProject`, `ProjectDetail` (applicant flow); `StaffDashboard`,
`StaffReview` (Staff Review & Analytics); `AdminRules` (configurable rule engine + API-client/user
admin); `Profile`. Components (`components/`): `Layout` (skip-to-content link + role-aware nav),
`DisclaimerBanner` (advisory-only), `FindingList`, `ClearanceList`, `ui.tsx`. The API client
(`api/client.ts` + `api/{auth,projects,reference,staff,admin}.ts`) attaches the Bearer JWT via an
interceptor; auth state lives in `hooks/useAuth.tsx` (exposes `isStaff` / `isAdmin`). Accessibility:
skip-to-content link, `:focus-visible` ring (`index.css`), `aria-*` labels, `role="status"` /
`role="alert"` live regions, semantic tables with `sr-only` captions, keyboard-operable controls.

---

## The async screening pipeline

Triggering is deliberately split from execution so the caller gets an immediate `PENDING`
run to poll — the same async contract the integration API exposes to City systems.

```
POST /api/projects/{id}/screen
        │
        ▼
PreCheckService.trigger(user, projectId, triggeredBy, actorLabel)
   • requireAccessible() → owner or staff
   • createPendingRun()  → PreCheckRun(status=PENDING) committed
   • auditService.record("SCREENING_TRIGGERED")
   • screeningService.runScreeningAsync(runId)  ── dispatches to the pool ──┐
   • returns RunDto (PENDING) immediately                                    │
                                                                             ▼
                                          @Async("screeningExecutor") @Transactional
                                          ScreeningService.runScreening(runId)
```

> **Transaction/async ordering note (from the code):** `PreCheckService.trigger` is
> deliberately **not** `@Transactional` — the PENDING run must be committed before the async
> worker starts, or the worker's `findById(runId)` would race the commit and see nothing.
> Both `@Async` and `@Transactional` sit on `runScreeningAsync`, so the transaction opens on
> the worker thread and lazy associations (`run.getProject().getParcel()`) have an open
> session for the whole run.

Inside `runScreening(runId)` the pipeline runs in order:

1. **Set PROCESSING** — mark `startedAt`, stamp `codeVersion` (`"LAMC/Title24 2024 seed"`).
2. **Extract text** — for each scan-`PASSED` document, `StorageService.extractText()` pulls
   text (PDFBox for PDF, POI for DOCX, raw read for text-based DXF; binary CAD/BIM stored but
   not parsed in the MVP). Per-doc `extractedTextChars` is recorded. Text is concatenated.
3. **Build context** — `ProjectContextBuilder.build(project, permitType, documents,
   combinedText)` → the flat `Map` the rules read (see `04-rule-engine.md`).
4. **Completeness** (`CompletenessService.evaluate`) — compares uploads against the permit
   type's required-doc checklist; flags missing required docs (BLOCKING), files that failed
   the security scan (BLOCKING), and the no-documents case (BLOCKING). Returns findings +
   required/present counts.
5. **Rule pre-screening** — the **primary** mechanism. Active `screening_rules` in priority
   order; for each, check `appliesTo(permitType)` and `evaluator.matches(condition, ctx)`;
   emit a `Finding` (source `RULE`).
6. **Clearance identification** — active `clearance_rules` in priority order; same
   applies-to + condition check; emit a `Clearance` (source `RULE`).
7. **AI augmentation** — `RegulatoryKnowledgeService.buildContext()` first retrieves the
   most relevant code sections (lexical keyword arm + pgvector cosine arm over e5-large-v2
   embeddings, RRF-fused k=60, top-8; vector arm silently off when the TEI sidecar is
   unreachable) and injects them into the prompt as "RELEVANT CODE CONTEXT".
   `AiAnalysisService.analyze()` then runs the active provider (Anthropic if keyed and
   available, else the heuristic). AI findings whose titles duplicate an existing finding
   are skipped (`containsTitle`); the rest are added (source `AI`). Code references are
   resolved to URLs via `RegulatoryKnowledgeService`. The provider + model used are stamped
   on the run.
8. **Persist + roll up** — `saveAll` findings and clearances; compute counts, score, and
   status; write the run summary (ending with the advisory-only disclaimer); set the run
   `COMPLETED`, `completedAt`, `processingMs`. Denormalize score/status onto the project and
   set `ProjectStatus.SCREENED`.
9. **Audit + webhook** — `auditService.recordSystem("SCREENING_COMPLETED", …)` and
   `webhookNotifier.notifyCompleted(run)` (webhook fires only for API-triggered runs).

On any exception the run is marked `FAILED` with the error message, `SCREENING_FAILED` is
audited, and the webhook still fires so the caller isn't left polling forever.

### Scoring & status (from `ScreeningService`)

```
score = 100 − (blocking × 12) − (warning × 5) − (info × 1)
        − (missingRequiredDocs × 6)          // extra weight on missing docs
score = clamp(score, 0, 100)

status = INCOMPLETE              if any COMPLETENESS finding is BLOCKING
       = REQUIRES_ATTENTION      else if blocking > 0 or warning > 0
       = READY_FOR_SUBMISSION    otherwise
```

The three statuses map directly to the RFP's applicant notification vocabulary
(SOW §2.2.2: "Ready for Submission / Requires Attention / Incomplete"). The run `summary`
always closes with: *"Results are advisory only and do not constitute Formal Plan Check
approval; final determinations are made by City of Los Angeles staff."* (SOW §2.2.9).

### The thread pool (`AsyncConfig`)

`screeningExecutor` is a `ThreadPoolTaskExecutor`: core 2, max 4, queue 50, thread prefix
`screening-`. Screening is off the request thread, which is what lets intake return
immediately and lets the integration API expose the same poll/webhook contract.

---

## Security

`SecurityConfig` sets up a **stateless** (`SessionCreationPolicy.STATELESS`), CSRF-disabled,
CORS-enabled filter chain with two authentication front doors and method-level security
(`@EnableMethodSecurity`).

### Two front doors
- **JWT (`JwtAuthFilter`)** — for interactive users. A `Bearer` token is resolved to a
  `UserDetails` principal (`UserDetailsServiceImpl`), validated by `JwtUtil`. In production
  this represents Angeleno Account (Auth0) for applicants and Okta SSO for staff; the MVP
  issues its own HS256 JWT after email+password login. Token TTL and secret come from
  `app.jwt.*`.
- **API keys (`ApiKeyAuthFilter`)** — for machine integrations on `/api/v1/**`. The
  `X-API-Key` header is SHA-256 hashed (`ApiKeyHasher`) and looked up via
  `findByKeyHashAndActiveTrue`. On a match, the filter sets a `ROLE_API_CLIENT` principal,
  stashes the `ApiClient` id as the `apiClientId` request attribute (for audit attribution),
  and stamps `lastUsedAt`. Raw keys are never stored.

Both filters run before `UsernamePasswordAuthenticationFilter`; the API-key filter runs first
and no-ops if a principal is already set.

### RBAC / route authorization
Roles: **APPLICANT**, **STAFF**, **ADMIN** (`User.Role`), plus the synthetic **API_CLIENT**
role granted by the API-key filter. Route rules:

| Matcher | Access |
|---|---|
| `POST /api/auth/login`, `POST /api/auth/register` | permit all |
| `/actuator/health` | permit all |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | permit all |
| `/api/v1/**` | `ROLE_API_CLIENT` |
| `/api/mcp/**` | `API_CLIENT`, `STAFF`, or `ADMIN` |
| `/api/staff/**` | `STAFF` or `ADMIN` |
| `/api/admin/**` | `ADMIN` |
| anything else | authenticated |

Within the applicant surface, ownership is enforced in the service layer:
`ProjectService.requireAccessible(user, projectId)` allows the owner, or any staff/admin, and
throws `403` otherwise.

Passwords use `BCryptPasswordEncoder`. An `AdminBootstrapRunner` idempotently promotes the
`app.bootstrap.admin-email` account to ADMIN on every boot so a fresh database is never
locked out of the staff/admin surfaces — modeling how the City would grant an initial
staff/admin via Okta group membership.

### Production security posture (designed, per SOW §4.4)
Zero-trust access controls, RBAC, encryption at rest and in transit, U.S.-based data hosting,
data minimization, regular backups and DR, and inclusion in the City Cyber Watch List. The
append-only `audit_log` provides auditability of all transactions (SOW §2.2.14, §4.2).

---

## Deployment

`docker-compose.yml` (at `app/`) defines three services on Colima:

| Service | Container | Host port → container | Image basis |
|---|---|---|---|
| `postgres` | `aip-ppc-postgres` | **5434** → 5432 | `backend/docker/postgres/Dockerfile` (Postgres 16) |
| `backend` | `aip-ppc-backend` | **8082** → 8080 | `backend/Dockerfile` (Spring Boot, Java 21) |
| `frontend` | `aip-ppc-frontend` | **8095** → 80 | `frontend/Dockerfile` (Vite build → nginx) |

Ports are offset from the Blue/munch stacks so all can coexist on one Colima VM. Postgres has
a `pg_isready` healthcheck; the backend `depends_on` it being healthy. Volumes: `postgres_data`
(DB) and `storage_data` (`/app/storage` uploads).

Quick start:
```bash
cd app
cp .env.example .env          # optionally add ANTHROPIC_API_KEY
docker compose up --build     # postgres :5434, backend :8082, frontend :8095
```
- App: `http://localhost:8095` · API: `http://localhost:8082/api` · Swagger:
  `http://localhost:8082/swagger-ui.html`
- Backend-only local dev: `docker compose up -d postgres` then `cd backend && ./gradlew
  bootRun` (backend on `:8080`).

Register an account, set `APP_BOOTSTRAP_ADMIN_EMAIL` to that email **in `app/.env`**
(default `admin@lacity.gov`), then recreate the backend with `docker compose up -d backend`
to gain STAFF+ADMIN access. A plain `docker compose restart` does **not** re-read `.env`;
and because compose always injects this variable, the `application.yml` default is only
consulted by non-Docker `bootRun`. Sign out/in afterwards so the JWT carries the new role.

---

## Configuration (`application.yml` + env vars)

All settings resolve from environment variables with in-file defaults. Key entries:

| Env var | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5434/aipppc` | Datasource URL (compose overrides to the `postgres` host) |
| `DB_USER` / `DB_PASS` | `aipppc` / `aipppc` | DB credentials |
| `PORT` | `8080` | Backend server port (mapped to 8082 by compose) |
| `JWT_SECRET` | dev placeholder (≥256-bit) | JWT signing key — **must be replaced in production** |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | JWT TTL |
| `APP_BOOTSTRAP_ADMIN_EMAIL` | see note | Email auto-promoted to ADMIN on boot. Compose always injects it (from `app/.env`, falling back to `admin@lacity.gov`), overriding the `application.yml` default — set it in `.env`, not the yml |
| `STORAGE_PATH` | `./storage` | Upload storage base (compose: `/app/storage`) |
| `STORAGE_MAX_FILE_BYTES` | `104857600` (100 MB) | Max upload size enforced by the scan |
| `EMBEDDING_ENABLED` | `true` | Vector-retrieval arm on/off (degrades to lexical-only when off/unreachable) |
| `EMBEDDING_URL` | `http://localhost:8086` | TEI sidecar URL (compose: `host.docker.internal:8086`; `http://tei:80` with the `docker-tei` profile). 8086, not Blue's 8081, to avoid a port collision on shared Colima VMs |
| `EMBEDDING_MODEL` / `EMBEDDING_DIM` | `intfloat/e5-large-v2` / `1024` | Embedding model + dimension (matches the `vector(1024)` column) |
| `KB_SCHEDULER_ENABLED` | `false` (compose: `true`) | Monthly knowledgebase refresh (SOW 2.2.13) |
| `KB_SCHEDULER_CRON` | `0 0 4 1 * *` | Refresh cadence — 04:00 on the 1st of each month |
| `SCREENING_TARGET_MS` | `1800000` (30 min) | KPI target for the analytics dashboard (SOW §2.2.10) |
| `AI_PROVIDER` | `anthropic` | AI provider: `anthropic` or `none` |
| `ANTHROPIC_API_KEY` | (blank) | Enables the Claude provider; blank → heuristic fallback |
| `ANTHROPIC_API_URL` | `https://api.anthropic.com/v1` | Anthropic base URL |
| `ANTHROPIC_MODEL` | `claude-sonnet-4-6` | Configured Claude model (see note) |
| `ANTHROPIC_MAX_TOKENS` | `4096` | Max output tokens per AI call |

Other static config: Flyway migrations at `classpath:db/migration` with
`baseline-on-migrate: true`; JPA `ddl-auto: validate` (schema is owned by Flyway, not
Hibernate); multipart limits 100 MB file / 110 MB request; springdoc Swagger UI at
`/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`.

> **Model-ID note:** the shipped default is `claude-sonnet-4-6`, a valid Claude Sonnet model
> ID. It is a per-run, single-variable override (`ANTHROPIC_MODEL`) — deployments can point
> it at the current recommended model (e.g. `claude-opus-4-8` or `claude-sonnet-5`) without a
> code change. The model actually used is recorded on each run as `aiModelUsed`.

---

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3.3.5, Java 21, Gradle (Kotlin DSL) |
| Security | Spring Security, jjwt 0.12.6 (HS256) |
| Database | PostgreSQL 16, Flyway migrations, Spring Data JPA (`validate`) |
| Document parsing | Apache PDFBox 3.0.3 (PDF text + report), Apache POI 5.3.0 (DOCX) |
| AI | Anthropic Claude via `java.net.http.HttpClient`; deterministic `HeuristicAiProvider` fallback |
| API docs | springdoc-openapi 2.6.0 / Swagger UI |
| Frontend | Vite + React 18 + TypeScript, TanStack Query, Tailwind (WCAG/ADA) |
| Runtime | Docker Compose on Colima |
