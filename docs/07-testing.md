# 07 — Testing Strategy

City of Los Angeles / LADBS — **AI-Powered Pre-Plan Check Assistant** (RFP 2025AIP007)

The test strategy has four layers, matching the Blue reference: backend **unit tests** for the
deterministic core, **`@WebMvcTest` controller slices** exercised through the real
`SecurityConfig`, **Testcontainers** repository/integration tests against a real Postgres, and
**vitest** for the frontend. The Gradle wiring (`testAll` task, Testcontainers dependencies,
Colima socket overrides) lives in `backend/build.gradle.kts`. Tests exist today across the unit,
controller-slice, and Testcontainers layers — currently **10 backend test classes** plus a shared
Postgres helper, and **2 frontend test files**. The per-layer notes below describe the intended
coverage; the highest-value targets not yet backed by a test file are marked **(planned)**.

Because the AI arm degrades to the deterministic `HeuristicAiProvider` when no Anthropic key is
present, **tests never require an API key** — the whole pipeline is reproducible offline.

### Current coverage (as shipped)

Backend (`backend/src/test/java/com/lacity/aipppc/`): `service/rules/RuleConditionEvaluatorTest`,
`service/screening/ScreeningScoringTest`, `service/screening/CompletenessServiceTest`,
`service/screening/TemplateRendererTest`, `service/rag/RrfFusionTest`, `service/AuditServiceTest`,
`service/ProjectServiceDeleteTest`, `controller/AuthControllerTest` (`@WebMvcTest` through the real
`SecurityConfig`), `integration/ScreeningIntegrationTest` (Testcontainers end-to-end screening +
seeder) and `integration/KnowledgeBaseIntegrationTest` (Testcontainers KB / pgvector pipeline,
`V4`), plus the shared `support/TestPostgres` container helper. Frontend:
`components/FindingList.test.tsx` and `lib/format.test.ts`.

---

## Commands

```bash
# Backend — unit + @WebMvcTest slices + Testcontainers integration tests
cd app/backend
./gradlew testAll        # points Testcontainers at Colima's Docker daemon (see below)
./gradlew test           # plain test task (uses whatever DOCKER_HOST is set)

# Frontend — vitest
cd app/frontend
npm run test:run         # single run (CI)
npm run test             # watch mode
```

`./gradlew testAll` is a dedicated task defined in `build.gradle.kts`; it exists specifically to
make Testcontainers work with Colima (details below).

---

## Layer 1 — Backend unit tests

Pure, fast tests of the deterministic core, no Spring context and no database. The highest-value
targets:

- **Rule evaluator** (`RuleConditionEvaluator`) — the JSON condition grammar is the load-bearing
  logic. Tests cover: each operator (`eq`, `neq`, `in`, `contains`, `containsAny`,
  `gt`/`gte`/`lt`/`lte`, `exists`, `missing`, `regex`); `all`/`any`/`not` nesting and
  short-circuiting; the numeric-vs-string coercion in `eq`; the deliberate asymmetry where
  `lt`/`lte` do **not** fire on a missing/non-numeric field while `gt`/`gte` treat missing as
  false; and the fail-safe path (malformed JSON, unknown operator, unknown node → `false`,
  never an exception).
- **Scoring & status** (`ScreeningService.computeScore` / `computeStatus`) — verify the formula
  `100 − 12·blocking − 5·warning − 1·info − 6·missingRequiredDocs`, the `[0,100]` clamp, and the
  status precedence: `INCOMPLETE` when any completeness finding is BLOCKING, else
  `REQUIRES_ATTENTION` when blocking/warning present, else `READY_FOR_SUBMISSION`.
- **Completeness** (`CompletenessService.evaluate`) — required-doc detection from
  `requiredDocsJson`, scan-failed/quarantined files flagged BLOCKING, the empty-uploads case,
  and correct `requiredCount` / `presentRequiredCount` roll-ups (which feed scoring).
- **Context builder** (`ProjectContextBuilder`) **(planned)** — parcel overlays/hazards flattening,
  dynamic-form keys added with `putIfAbsent` (built-ins not shadowed), `presentDocs` from
  scan-passed docs, `missingDocs` from the checklist, and the combined `text` field.
- **Template renderer** (`TemplateRenderer`) — `{{placeholder}}` substitution, `(unspecified)`
  for null, list join, and whole-number formatting.
- **Confidence bucketing** (`ConfidenceLevel.fromScore`) **(planned)** — the `85 / 55` thresholds.
- **UPID generation** (`ProjectService`) **(planned)** — format `LA-<year>-<6 digits>` and collision retry.
  (`ProjectServiceDeleteTest` currently covers deletion authorization, not UPID format.)
- **Heuristic AI** (`HeuristicAiProvider`) **(planned)** — deterministic keyword signals and the
  "limited machine-readable content" finding when no text is extracted.

These need no Colima and run in milliseconds.

---

## Layer 2 — `@WebMvcTest` controller slices (with SecurityConfig)

Each controller is tested in a web slice with mocked services, but with the **real security
filter chain** imported so that JWT/API-key authentication and the RBAC route matchers are
exercised (`@Import(SecurityConfig.class)` plus the JWT/API-key filters). Use
`spring-security-test` (`@WithMockUser`, `SecurityMockMvcRequestPostProcessors`) — it's already
a `testImplementation` dependency.

What the slices assert (`AuthControllerTest` is implemented; the remaining slices are **planned**):
- **AuthController** — register/login return a token; `/me` requires a valid Bearer token;
  `register`/`login` are permit-all while `/me`, `/profile`, `/change-password` require auth.
- **ProjectController** **(planned)** — create/list/get/update/upload/screen/report happy paths; ownership
  enforcement (owner vs. another applicant vs. staff → `403` for the non-owner non-staff case);
  multipart upload binding; the PDF report content type; `204` from `runs/latest` when no run
  exists.
- **RunController** **(planned)** — run detail access control; the flag endpoint requires a non-blank comment
  (bean validation → `400`).
- **ReferenceController** **(planned)** — permit-type and parcel/code search shapes.
- **Route authorization** **(planned)** — anonymous access to `/api/v1/**`, `/api/staff/**`, `/api/admin/**`
  is rejected; a valid `X-API-Key` yields `ROLE_API_CLIENT`; a JWT without STAFF/ADMIN cannot
  reach staff/admin routes.

Slices exercise `GlobalExceptionHandler` mappings (e.g. `ApiException.notFound → 404`,
validation → `400`) without a database.

---

## Layer 3 — Testcontainers repository & integration tests

Full-stack persistence tests against a **real PostgreSQL 16** container, so Flyway migrations,
JPA mappings (`ddl-auto: validate`), JSON `text` columns, indexes, and cascade/`SET NULL`
foreign keys are all verified for real. Dependencies already present:
`spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`,
`org.testcontainers:postgresql`.

Coverage:
- **Migrations** — `V1`/`V2`/`V3` apply cleanly on an empty container; `ddl-auto: validate`
  confirms the entities match the schema.
- **Repositories** — custom finders:
  `ScreeningRuleRepository.findByActiveTrueOrderByPriorityAsc`,
  `PreCheckRunRepository.findTopByProjectOrderByCreatedAtDesc`,
  `ApiClientRepository.findByKeyHashAndActiveTrue`,
  `ProjectRepository.existsByUniversalProjectId`, parcel/code search.
- **Seeder** — `ReferenceDataSeeder` loads the JSON corpus idempotently (re-running does not
  duplicate rows or clobber edits, keyed by natural key).
- **End-to-end screening** — create user → project → upload a document → run
  `ScreeningService.runScreening` **synchronously** (the sync entry point exists precisely for
  tests) → assert findings, clearances, score, status, and denormalized project fields. Using
  the sync path avoids async timing flakiness while exercising the whole pipeline with the
  heuristic AI provider.

---

## Colima / Testcontainers setup

Local development uses **Colima** (a Docker runtime on macOS) rather than Docker Desktop, so
Testcontainers must be told where the Docker socket lives. This mirrors the Blue project's
`CLAUDE.md` guidance. `build.gradle.kts` handles it two ways:

1. On the plain `test` task, `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` is set to
   `/var/run/docker.sock` — the path Testcontainers uses to talk to the daemon from within
   containers (Ryuk, etc.).
2. The convenience task `testAll` additionally sets `DOCKER_HOST` to Colima's socket:

```kotlin
tasks.register<Test>("testAll") {
    val colimaSocket = "${System.getProperty("user.home")}/.colima/default/docker.sock"
    environment("DOCKER_HOST", "unix://$colimaSocket")
    doFirst {
        require(file(colimaSocket).exists()) {
            "Colima Docker socket not found at $colimaSocket. Start Colima first: `colima start`."
        }
    }
}
```

So the workflow is: `colima start`, then `./gradlew testAll`. The `doFirst` guard fails fast
with an actionable message if Colima isn't running, rather than letting Testcontainers hang.

> If you use Docker Desktop instead of Colima, run `./gradlew test` (it uses the ambient
> `DOCKER_HOST`), or export `DOCKER_HOST` to your Docker Desktop socket.

---

## Layer 4 — Frontend (vitest)

The React app uses **vitest** with `jsdom`, `@testing-library/react`,
`@testing-library/user-event`, and `@testing-library/jest-dom` (all in `devDependencies`).
Scripts: `npm run test` (watch) and `npm run test:run` (single run for CI).

Coverage focus:
- Rendering of findings by severity and confidence bucket, clearance cards, and the readiness
  score/status badge.
- The dynamic intake form driven by a permit type's `formSchema` (field types `number`,
  `boolean`, `select`; `showIf` show/hide behavior).
- Auth flow and TanStack Query hooks (mocked API via axios).
- **Accessibility** — keyboard navigation and ARIA labeling, since the RFP mandates WCAG/ADA
  conformance including full keyboard operability (SOW §2.2.10, §4.5; Appendix 3 §6.1.6,
  technical req 10).

---

## CI expectations

- `./gradlew testAll` runs the full backend suite (unit + web slice + Testcontainers) with a
  Docker daemon available.
- `npm run test:run` runs the frontend suite headlessly.
- No secrets required: the AI provider falls back to the deterministic heuristic, so backend
  integration tests produce stable, reproducible findings.

Both commands are also listed in the project `README.md` under **Tests**.
