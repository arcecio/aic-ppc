# AI-Powered Pre-Plan Check Assistant (AIP PPC)

**City of Los Angeles · Department of Building and Safety (LADBS) — RFP 2025AIP007**

An advisory, vendor-hosted web application and API that helps applicants validate
development submittals **before** they file a Formal Plan Check. It performs
completeness validation, configurable **rule-based** + **AI-assisted**
pre-screening, and departmental **clearance identification**, and gives City staff
a review-and-analytics console with a human-in-the-loop guardrail.

> **Advisory only.** This tool does **not** perform Formal Plan Check and does
> **not** issue permits. City of Los Angeles staff retain authority over all final
> determinations (Scope of Work §1.1, §2.2.9).

This MVP mirrors the architecture and approach of the
[Blue](https://github.com/arcecio/Blue) reference (Spring Boot + React + Postgres
on Docker/Colima) and is deployed to <https://github.com/arcecio/aic-ppc>.

---

## Three modes of operation (SOW §1.2)

1. **Applicant-facing Pre-Plan Check** — sign in, create a project (auto-assigned a
   BuildLA-style **Universal Project ID**), fill a dynamic permit-type form, upload
   plans (security-scanned), run a pre-plan check, view findings + likely
   clearances, and export a PDF compliance report.
2. **Staff-facing Review & Analytics** — KPI dashboards, run review with
   accept/modify/reject on every AI/rule finding (human-in-the-loop), a
   configurable rule engine (no vendor code changes), and a feedback inbox.
3. **Integration API** — a secure, versioned REST surface (`/api/v1`) for ePlanLA
   and other City systems: submit a project, screen headlessly, poll status,
   retrieve JSON results, and receive async webhooks. Plus a lightweight MCP tool
   endpoint.

## Tech stack

| Layer     | Choice |
|-----------|--------|
| Backend   | Spring Boot 3.3, Java 21, Gradle (Kotlin DSL) |
| Database  | PostgreSQL 16 + **pgvector** (Flyway migrations, JPA `validate`) |
| AI        | Anthropic Claude (`AnthropicAiProvider`) with a deterministic offline `HeuristicAiProvider` fallback |
| Retrieval | Hybrid knowledgebase RAG: lexical + pgvector cosine (e5-large-v2 via optional TEI sidecar), RRF-fused; degrades to lexical-only |
| Docs      | Plan text via Apache PDFBox (PDF) & POI (DOCX); PDF report via PDFBox |
| API docs  | springdoc OpenAPI / Swagger UI |
| Frontend  | Vite + React 18 + TypeScript, TanStack Query, Tailwind (WCAG/ADA) |
| Runtime   | Docker Compose on Colima |

## Quick start (Colima / Docker)

```bash
cd app
cp .env.example .env          # optionally add ANTHROPIC_API_KEY
docker compose up --build     # postgres :5434, backend :8082, frontend :8095
```

- App: <http://localhost:8095>  ·  API: <http://localhost:8082/api>  ·  Swagger: <http://localhost:8082/swagger-ui.html>

**Becoming staff/admin.** Set `APP_BOOTSTRAP_ADMIN_EMAIL=you@example.com` in `app/.env`,
register that account in the UI, then recreate the backend container:

```bash
docker compose up -d backend   # NOT `restart` — restart does not re-read .env
```

Watch for `Bootstrap: promoted you@example.com to ADMIN` in `docker compose logs backend`,
then sign out and back in so your JWT picks up the new role.

> Note on precedence: the compose file always injects `APP_BOOTSTRAP_ADMIN_EMAIL` into the
> container (from `app/.env`, falling back to `admin@lacity.gov`), and that env var
> **overrides** the default in `application.yml`. Editing `application.yml` has no effect on
> the Docker stack (it's baked into the jar and only used by non-Docker `bootRun`).

### Backend only (local dev)

```bash
cd app && docker compose up -d postgres      # just the DB on :5434
cd backend && ./gradlew bootRun               # backend on :8080
```

### Tests

```bash
cd backend && ./gradlew testAll   # unit + @WebMvcTest + Testcontainers (needs Colima)
cd frontend && npm run test:run   # vitest
```

Ports are offset from the Blue/munch stacks so all can coexist on one Colima VM.

## How screening works (SOW §2.2.3)

Rules are the **primary** mechanism; AI **augments** them. A run executes:

1. **Extract** text from scan-passed documents (PDFBox/POI).
2. **Completeness** — compare uploads against the permit type's required-doc checklist.
3. **Rule pre-screening** — evaluate the configurable `screening_rules` against the
   project context (permit type, parcel zone/overlays/hazards, form answers, text).
4. **Clearance identification** — evaluate `clearance_rules` per department.
5. **AI augmentation** — Claude (or the offline heuristic) adds findings the rules missed.
6. **Score & status** — compute a submission-readiness score and status
   (Ready for Submission / Requires Attention / Incomplete).

Every finding and clearance is advisory, carries a **confidence** score and code
**reference/link**, and enters staff review as `PENDING` (human-in-the-loop).

## Knowledgebase: updates & retrieval (SOW §2.1)

The regulatory corpus (`regulatory_codes`: LAMC Ch. I/1A/IX, Title 24, CALGreen,
CBC 11B, Clearance Handbook extracts) is **upserted by `external_id`** — amendments
update sections in place, never duplicate. Three update paths, all audited:

1. **Release corpus** — the bundled `seed/regulatory-codes.json` re-syncs on every boot.
2. **Admin import** — `POST /api/admin/regulatory-codes/import` bulk-upserts a corpus
   drop between releases (plus full CRUD and `POST …/sync`, `GET …/status`).
3. **Scheduler** — a monthly refresh (`KB_SCHEDULER_ENABLED`, default cron
   `0 0 4 1 * *`) re-runs sync + embedding backfill (SOW §2.2.13).

Retrieval to the LLM is **hybrid RAG**: a lexical arm (keyword search) and a vector
arm (pgvector HNSW cosine over e5-large-v2 embeddings) fused with Reciprocal Rank
Fusion; the top sections are injected into Claude's prompt as code context, and AI
citations are resolved back to canonical URLs. The vector arm needs the optional
**TEI sidecar**:

```bash
# macOS (Apple Silicon — native binary; the amd64 TEI image crawls under Rosetta):
brew install text-embeddings-inference
text-embeddings-router --model-id intfloat/e5-large-v2 --port 8086

# linux/amd64:
docker compose --profile docker-tei up   # then EMBEDDING_URL=http://tei:80
```

Without it, everything still works — retrieval silently degrades to lexical-only.

## Documentation

See [`docs/`](docs/):

- `01-overview.md` — product scope & the three modes
- `02-architecture.md` — services, request flow, deployment
- `03-domain-model.md` — entities & schema
- `04-rule-engine.md` — the configurable rule condition grammar
- `05-ai-and-governance.md` — AI usage, Exhibit 7 disclosures, human-in-the-loop
- `06-api.md` — integration API & MCP
- `07-testing.md` — test strategy
- `08-requirements-traceability.md` — RTM: requirement → implementation
- `09-rbac-design.md` — production RBAC design (roles, scopes, maker-checker) — plan of record, not yet implemented
- `10-cost-model.md` — Exhibit 5 cost-form fill-in guide (milestone weights, recurring lines, LLM COGS, T&M rate card)

## Repository layout

```
app/
├── backend/     Spring Boot service (engine, rules, AI, API)
├── frontend/    React web app (applicant + staff)
├── docs/        Architecture, API, RTM, governance
└── docker-compose.yml
```
