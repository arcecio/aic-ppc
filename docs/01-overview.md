# 01 — Product Overview

City of Los Angeles · Department of Building and Safety (LADBS) — **AI-Powered Pre-Plan Check
Assistant** (RFP 2025AIP007)

> **Advisory only.** This tool does **not** perform Formal Plan Check and does **not** issue
> permits. City of Los Angeles staff retain authority over all final determinations
> (SOW §1.1, §2.2.9).

---

## What it is

The AIP PPC Assistant is an advisory, vendor-hosted web application and API that helps
applicants validate development submittals **before** they file a Formal Plan Check in ePlanLA.
For each project it performs:

- **Completeness validation** — is every required plan, calculation, and form present and
  organized? (SOW §2.2.2)
- **Configurable rule-based + AI-assisted pre-screening** — likely zoning, building,
  accessibility, fire/overlay, and other code issues, each with a severity, confidence, and
  cited code reference. (SOW §2.2.3, §2.2.4)
- **Departmental clearance identification** — which City departments (Planning, LAFD, BOE, BOS,
  BSS, BCA, LAHD, LADWP, DOT, LADBS) will likely require a clearance, why, and what to submit.
  (SOW §2.2.5)

It then produces a **submission-readiness score and status** and an exportable **PDF compliance
report**, and gives City staff a review-and-analytics console with a human-in-the-loop
guardrail on every AI/rule finding.

The project is aligned with the City's modernization goals and **Executive Directive No. 19
(ED19)**, which calls for AI-enabled tools that validate completeness and code compliance before
formal filing, address multi-departmental clearances for **multi-family residential and
commercial** projects (the ED19 priority types), and give staff dashboards for comparative
performance metrics.

---

## Primary objectives (SOW §1.1)

The system is designed to:

- Automate Pre-Plan Check **completeness validation** — required documents (including plans and
  calculation sheets) present, and forms filled completely and accurately.
- Enable **early identification** of required City departmental reviews and clearances.
- Provide **advisory pre-screening** of potential code compliance issues from a core regulatory
  knowledgebase.
- Integrate with existing City information and permitting systems (ePlanLA, future LACPS,
  BuildLA, ZIMAS/NavigateLA GIS, Angeleno Account / Okta).
- **Reduce Plan Check correction cycles** and **improve submission quality and permit
  processing timelines** — the core ED19 outcome metrics.
- Enhance coordination across City departments involved in permit clearances.
- Support City staff through data analytics and performance insights.
- **Implement Model Context Protocol (MCP)** — a standardized way for AI models to securely
  connect to external tools, databases, and data sources.

---

## The three modes of operation (SOW §1.2)

The RFP defines three primary modes; the system implements each.

### 1.2.1 — Applicant-facing Pre-Plan Check
An Angeleno-account applicant signs in, creates a project record (assigned a BuildLA-style
**Universal Project ID**), fills a **dynamic, permit-type-specific intake form** whose fields
show/hide by project scope, uploads plans and supporting documents (each **security-scanned**
before AI integration), runs a pre-plan check, reviews the findings and likely clearances with
confidence indicators and code links, and exports a **PDF compliance report**. A submission
history dashboard supports version comparison across resubmittals.

*Backing code:* `AuthController`, `ProjectController`, `RunController`, `ReferenceController`,
`ProjectService`, `PreCheckService`, `StorageService`, `ReportService`, and the screening
pipeline. **Implemented.**

### 1.2.2 — Staff-facing Review & Analytics
City reviewers get dashboards and reports over submitted/completed applications (counts, errors
identified, clearances identified, processing time, submission timestamps), can **review, and
accept / modify / reject** every AI or rule-generated finding and clearance before final
disposition (human-in-the-loop), can **configure the rule engine, code references, thresholds,
and policy parameters without vendor code changes**, and manage a feedback inbox for continuous
improvement. KPI capture feeds City data platforms (PowerBI / SAP) for ED19 performance
tracking.

*Backing code:* `StaffController` (`/api/staff/**`) + `StaffService` + `AnalyticsService`
(analytics/KPIs, project & run browsing, finding/clearance review with `StaffDisposition`
accept/modify/reject, feedback inbox), `AdminController` (`/api/admin/**`) + `RuleAdminService`
+ `AdminUserService` + `ApiClientService` (configure `screening_rules` / `clearance_rules`,
manage API clients, users/roles, audit log) — all without vendor code changes. On the frontend,
`pages/StaffDashboard.tsx`, `pages/StaffReview.tsx`, and `pages/AdminRules.tsx`. KPI capture
(`usedAipPpc` / `triggeredBy`, `PreCheckRun` timings, `audit_log`) feeds the analytics endpoint;
live PowerBI/SAP export remains designed. **Implemented.**

### 1.2.3 — Integration API
A secure, versioned REST surface (`/api/v1`) for existing City applications — notably **ePlanLA**
and the future **LACPS** (Salesforce/Clariti) — to submit permit data, metadata, and plan
files; **run the pre-screening engine headlessly**; poll submission status; and retrieve
**structured JSON results** (issues, completeness findings, potential clearances,
confidence/score indicators, timestamps, reference info). It supports **asynchronous processing
and webhooks** so an originating portal is notified the moment analysis completes, plus a
lightweight **MCP tool endpoint** (`/api/mcp`).

*Backing code:* `IntegrationApiController` (`/api/v1/**`) + `IntegrationService` expose submit,
document upload, headless `screen`, a one-call `/api/v1/screenings` (submit+screen), and
poll/retrieve (`/api/v1/runs/{runId}` status, `/api/v1/runs/{runId}/results` structured JSON);
`ApiKeyAuthFilter` + `ApiKeyHasher` authenticate `X-API-Key` → `ROLE_API_CLIENT`; the `ApiClient`
entity carries webhook URLs and `ScreeningWebhookNotifier` fires `screening.completed` on
API-triggered run completion. `McpController` serves a JSON-RPC 2.0 MCP endpoint at `/api/mcp`
(`initialize`, `tools/list`, `tools/call`; tools `lookup_parcel`, `list_permit_types`,
`identify_permit`, `search_codes`). **Implemented.** Live ePlanLA/LACPS system integration
(the external side of the contract) remains designed.

---

## Advisory / human-in-the-loop principle (SOW §1.1, §2.2.9, §4.1.3, §4.3)

This is the defining constraint of the system, enforced end-to-end:

- The role of the Assistant is **advisory**. It **neither performs Formal Plan Check review nor
  automatically issues permits.** City staff maintain authority over **all final
  determinations.**
- The system **never blocks submission** — completeness validation "shall not prevent users from
  submitting applications, but shall provide guidance to improve submission quality"
  (SOW §2.2.2). This is why `ProjectStatus` has no "rejected" state and the readiness score is a
  notification, not a gate.
- Every finding and clearance enters staff review as **`PENDING`** and carries a
  **`StaffDisposition`** (accept / modify / reject) — the "human in the loop" mechanism
  (SOW §4.1.3; Appendix 3 §5.1.5). Applicants can also **flag an inaccurate finding** to notify
  City staff (SOW §2.2.4).
- Every AI/rule output is **explained**: a triggering condition, any assumptions, a cited code
  reference (with a link where possible), and a confidence indicator (High/Medium/Low).
- The PDF report and run summary lead with the disclaimer: *"Results are advisory only … do not
  constitute Formal Plan Check approval … final determinations are made by City of Los Angeles
  staff."*
- AI **augments** the rule engine; rules are the **primary** mechanism (SOW §2.2.3). See
  `05-ai-and-governance.md`.

---

## Phased approach (SOW §6)

The architecture is designed for scalability and future expansion **without significant
re-platforming**.

### Phase 1 — Pre-Plan Check Assistant (SOW §6.1)
Initial deployment focuses on **applicant-facing Pre-Plan Check** to improve submission quality,
identify required clearances faster, and reduce correction cycles. Scope:

- AI knowledgebase development (regulatory codes, clearance handbook).
- Completeness validation.
- **Rule-based and AI-assisted pre-screening.**
- **Advisory, rules-based clearance identification.**
- Compliance reporting and submission-readiness indicators.
- Analytics and dashboards aligned with ED19 performance tracking.
- Support for plan formats: **PDF, CAD, DXF, BIM, DOCX**.

This is the scope this reference implementation targets. All three modes **exist today**: the
applicant flow, rule engine, AI providers, completeness, clearances, PDF report, and audit log;
the staff Review & Analytics mode (`/api/staff/**` + `/api/admin/**` and the React staff/admin
pages); and the `/api/v1` integration API plus the `/api/mcp` MCP endpoint. What remains designed
is the live City-system integration on the far side of those APIs (Auth0/Okta SSO, BuildLA,
ZIMAS/NavigateLA, ePlanLA/LACPS) and advanced analysis (OCR/CAD-BIM deep parsing, PowerBI/SAP
export dashboards).

### Phase 2 — Internal AI Assistance and LACPS Integration (SOW §6.2)
Expansion into staff-facing tools and deeper system integration:

- Formal Plan Check assistant capabilities for City staff.
- A staff interface for reviewing AI-generated findings.
- Integration with **LACPS** and related systems.
- Support for project review workflows, issue tracking, and corrections.
- Continued use of AI to **assist, not replace,** City Formal Plan Check decision-making.

---

## Environments & delivery

The solution is vendor-hosted and City-branded, deployed across **Sandbox, UAT, and
Production** environments, with training and knowledge transfer (admin, end-user,
train-the-trainer), a hyper-care period, and deliverable documentation including a
**Requirements Traceability Matrix** (Deliverable 1.3), API docs, system architecture, model
governance/retraining policies, and security documentation (SOW §3, §5). Comparative baseline
vs. post-implementation performance metrics (Deliverable 9.5) tie back to the ED19 KPI goals.

---

## Document map

| Doc | Contents |
|---|---|
| `01-overview.md` | Product scope, three modes, objectives, advisory principle, phasing (this doc) |
| `02-architecture.md` | Components, service map, async screening pipeline, security, deployment, config |
| `03-domain-model.md` | Every entity + enum, ER overview, Universal Project ID & readiness concepts |
| `04-rule-engine.md` | The configurable condition grammar, context fields, worked seed examples |
| `05-ai-and-governance.md` | AI usage, providers, and the completed Exhibit 7 disclosures |
| `06-api.md` | REST endpoints (auth/applicant/reference, staff/admin, `/api/v1` + MCP — all implemented) |
| `07-testing.md` | Unit, `@WebMvcTest`, Testcontainers, and vitest strategy (Colima) |
| `08-requirements-traceability.md` | The RTM: requirement → implementing component → status |
