# 05 — AI Usage & Governance

City of Los Angeles / LADBS — **AI-Powered Pre-Plan Check Assistant** (RFP 2025AIP007)

This document covers how AI is used in the system (and how it is deliberately constrained),
then presents a completed **Exhibit 7 — Artificial Intelligence System Technical Disclosures**
for this reference implementation, followed by the broader governance posture (bias,
accessibility, transparency, data minimization, hosting, audit).

---

## AI usage: rules are primary, AI augments

The RFP is explicit (SOW §2.2.3): "configurable rule-based logic … **as the primary mechanism**
with **AI-assisted analysis used to enhance** detection and interpretation of plan content." The
implementation honors this ordering literally.

In `ScreeningService.runScreening()` the sequence is:

1. Completeness validation (deterministic).
2. **Rule-based screening** — the configurable `screening_rules` (the primary mechanism).
3. **Rule-based clearance identification** — the configurable `clearance_rules`.
4. **AI augmentation** — the AI provider is called **last**, is given the titles of the findings
   the rules already produced, and is instructed to surface **only** issues the rules missed.
   Any AI finding whose title duplicates an existing rule/completeness finding is dropped
   (`containsTitle`).

So AI never replaces or overrides the rule engine; it adds a supplementary, clearly-labeled
(`source = AI`) layer on top of deterministic results. If AI is disabled or unavailable, the
system still produces a complete, valid screening from rules + completeness alone.

### Provider architecture (`service/ai/`)

The AI arm is a small, pluggable abstraction:

- **`AiProvider`** — interface: `type()`, `available()`, `analyze(AiRequest)`.
- **`AnthropicAiProvider`** — calls the Anthropic **Messages API** over
  `java.net.http.HttpClient`. It sends the project info, narrative, extracted document text
  (truncated to 24,000 chars), the retrieved regulatory-code context, and the rules' finding
  titles; it asks for **strict JSON** findings with category, severity, code reference,
  confidence (0–100), recommendation, and triggering condition. It is `available()` only when an
  API key is configured; on any non-2xx response or exception it returns
  `AiAnalysis.unavailable()` and logs — it **never** hard-fails the screening.
- **`HeuristicAiProvider`** — a deterministic, fully-offline keyword scanner. It matches a fixed
  set of signals (egress, sprinkler, restroom, parking, stair, occupancy) against the combined
  project + document text and emits advisory findings the pure rule engine might miss, plus a
  "limited machine-readable content" note when no text could be extracted. It is **always**
  `available()`. This keeps the system functional (and tests reproducible) with no API key.
- **`AiAnalysisService`** — selects the provider. Preference order: the configured provider
  (`app.ai.provider`, default `anthropic`) **if it is available**, otherwise the heuristic. If
  the configured provider bails out at call time (unavailable or throws), it transparently falls
  back to the heuristic. The provider and model actually used are stamped on the run as
  `aiProviderUsed` / `aiModelUsed`.

### System prompt guardrails

The Anthropic system prompt (in `AnthropicAiProvider`) encodes the advisory principle directly:

> "You are an advisory pre-plan-check assistant for the City of Los Angeles LADBS. You AUGMENT a
> rule-based engine — surface only issues the listed rule findings did not already cover. You
> never approve plans; City staff make all final determinations. … Cite specific LAMC / Title 24
> / CBC sections where possible. Be conservative with confidence."

Configuration is entirely env-driven (`app.ai.*` in `application.yml`): `AI_PROVIDER`
(`anthropic` | `none`), `ANTHROPIC_API_KEY`, `ANTHROPIC_API_URL`, `ANTHROPIC_MODEL` (default
`claude-sonnet-4-6`; overridable per deployment to the current recommended model without a code
change), `ANTHROPIC_MAX_TOKENS`.

---

## Exhibit 7 — AI System Technical Disclosures (completed)

The following completes **Exhibit 7** (per RFP §4.2.7; Standard Provisions for City Contracts,
Rev. 5/26 [v.1], PSC-23) for **this reference implementation**. It is written to reflect how the
system is actually built.

### Section 1 — Contractor & Contract Information

| Field | Value |
|---|---|
| Contractor Name | *(Proposer)* — reference implementation submitted for RFP 2025AIP007 |
| Contract/Project Name | AI-Powered Pre-Plan Check Assistant (AIP PPC) |
| City Department | Department of Building and Safety (LADBS), with the Department of City Planning |
| Date of Submission | *(submission date)* |
| New disclosure or material change? | **[X] New** |

### Section 2 — Technical Disclosures (check all that apply)

- **[X] Processes City Data** — ingests applicant-submitted plans, project metadata, and City
  parcel/zoning/GIS reference data for screening.
- **[X] Generates deliverables for the City** — produces structured findings, clearance lists,
  a submission-readiness score/status, and an exportable PDF compliance report.
- **[X] Interacts with the public or City personnel on the City's behalf** — the applicant-
  facing web UI presents AI-assisted findings to external applicants; the staff console presents
  them to City reviewers.
- **[ ] Materially informs services or decisions under the Contract** — **NOT checked.** The
  system is advisory only; it does not make or materially inform final determinations. Every
  output is subject to human review, and City staff retain authority over all determinations
  (see Section 6.3).

### Section 3 — AI System & Provider Details

| Field | Value |
|---|---|
| Name of AI System/Tool | AI-Powered Pre-Plan Check Assistant (AIP PPC) |
| AI Provider/Developer | **Primary detection:** configurable rule-based engine (custom, City-configurable — no third-party model). **AI augmentation:** Anthropic Claude via the Anthropic Messages API. **Offline fallback:** a deterministic in-system heuristic (`HeuristicAiProvider`) that uses no external model. |
| Material Version or Model Family | Anthropic **Claude** (Sonnet family; configured via `ANTHROPIC_MODEL`, recorded per run as `aiModelUsed`). Rule engine + heuristic are versioned in the application; the applied code edition is stamped per run as `codeVersion`. |
| Hosting Region | **U.S.-based hosting** for the application, database, and file storage (SOW §4.4.3). The Anthropic API is invoked over TLS from U.S. infrastructure. When no API key is configured, the system runs **fully offline** with no external model calls. |

### Section 4 — Use Case & Data Processing

**1. Intended Use Case.** The AI System performs **advisory pre-plan-check screening** of
development submittals **before** Formal Plan Check. It (a) validates submission completeness
against the permit type's required-document checklist; (b) applies a configurable rule engine to
flag likely zoning, building, accessibility, fire/overlay, structural, and green-code issues,
each with a severity (Blocking/Warning/Informational), a confidence indicator, and a cited code
reference; (c) identifies likely departmental clearances with submittal requirements; and (d)
produces a submission-readiness score/status and an exportable PDF report. AI (Claude, or the
offline heuristic) is used **only to augment** the rule engine — surfacing supplementary,
clearly-labeled findings the rules did not already cover. The system does **not** perform Formal
Plan Check, does **not** issue permits, and does **not** make final determinations.

**2. Categories of City Data Processed.**
- Applicant-submitted **plans and supporting documents** (Vector PDF, DOCX, DXF; CAD/BIM stored
  but not parsed in the MVP) and **extracted text** therefrom.
- **Project metadata**: title, permit type, project scope, intended use, description, and
  dynamic-form answers.
- **Address / parcel / GIS reference data**: APN, address, zone, overlays, hazard zones,
  council district, community plan area (sourced from City GIS such as ZIMAS / NavigateLA).
- **Regulatory knowledgebase**: LAMC, Title 24, CBC, and Clearance Handbook code sections
  (public reference material, not personal data).
- **Account identity**: applicant name, email, and organization (via Angeleno Account / Okta in
  production). PII is minimized (see Section 6, and §4.4.1).

### Section 5 — Data Retention & Training Restrictions

**1. Retention Practices.** City Data (projects, documents, screening runs, findings,
clearances) is retained in the City-hosted database and file store **only as long as necessary**
to fulfill the pre-plan-check purpose and support staff review/analytics, consistent with the
City's data-minimization requirement (SOW §4.4.1) and applicable records-retention policy.
Uploaded files live in City-controlled storage. The append-only `audit_log` records processing
activity for auditability (SOW §2.2.14, §4.2). Applicant/project data can be deleted per the
cascade relationships in the schema.

**2. Exception for Abuse Monitoring / Troubleshooting.**
**[ ] Yes [X] No** — City Data is not retained by the AI provider for abuse monitoring, safety
review, or troubleshooting under this reference implementation's default configuration. The
Anthropic API is called for the single screening task and the response is consumed inline;
prompts/outputs are not sent to the provider for retention. *(Any provider-side retention would
be governed by the executed contract's data-processing terms and disclosed here.)*

**3. Model Training Acknowledgment.** **City Data will NOT be used to train, retrain, fine-tune,
or otherwise improve any AI System or model** without the express written authorization of the
City via a fully executed amendment. This aligns with SOW §2.2.13 ("No use of City of Los
Angeles data without explicit written approval"; all learning transparent and auditable; model
updates subject to City review and approval). *Initials: ____*

### Section 6 — Limitations, Oversight, & Risk Management

**1. Material Limitations.**
- The AI-assisted layer is **advisory and probabilistic**; findings carry explicit confidence
  indicators (High/Medium/Low) and may include false positives or miss issues. It does not
  substitute for Formal Plan Check.
- **Document parsing** is limited to vector PDFs, DOCX, and text-based DXF in the MVP; scanned
  (image-only) PDFs and binary CAD/BIM yield little or no extractable text, which reduces
  detection depth — the system explicitly flags "limited machine-readable content" in that case.
- The rule engine and knowledgebase reflect a **seed code edition** (stamped per run as
  `codeVersion`); code amendments must be incorporated within 30 days of publication (SOW
  §2.1.4) — until then, findings reflect the prior edition.
- External-model availability is not guaranteed; the system **degrades gracefully** to a
  deterministic heuristic, which is narrower in scope.
- Accuracy targets (≥90% code-violation identification, SOW §2.1.4) are validated pre-deployment
  and monitored (precision/recall/agreement/false-positive/false-negative), with quarterly
  performance reports to the City.

**2. Human Oversight & Governance.**
- **Documented intended use:** advisory pre-plan-check only (this document and the SOW).
- **Human-in-the-loop:** every AI/rule finding and clearance enters staff review as `PENDING`
  and carries a `StaffDisposition` (accept / modify / reject) before final disposition;
  applicants can flag inaccurate findings (SOW §4.1.3, §2.2.4).
- **Change management:** the rule engine, code references, thresholds, and policy parameters are
  City-configurable **without vendor code changes** (SOW §2.2.3, §2.1.6); the reference
  knowledgebase and rule packs are idempotently seeded and versioned; all model/rule updates are
  subject to City review and approval, and are auditable (SOW §2.2.13).
- **Testing for material errors/security risks:** unit, controller-slice, and Testcontainers
  integration tests (see `07-testing.md`); uploaded files pass an automated security scan before
  AI integration; the system runs on the City Cyber Watch List for frequent vulnerability scans
  (SOW §4.4.2).
- **Quality controls:** confidence scoring and bucketing, the feedback inbox for continuous
  improvement, and the append-only audit trail of all transactions.

**3. Material Decisions.** Will the AI-generated output be relied upon for material legal,
financial, employment, eligibility, benefits, enforcement, or safety decisions affecting any
individual? **[ ] Yes [X] No.** The output is **advisory only**. It does not constitute Formal
Plan Check approval and does not issue permits; City staff make all final determinations
(SOW §1.1, §2.2.9, §4.3). Because it is advisory and every output is subject to human review
before any City action, no material decision is made by the AI System.

### Section 7 — Contractor Certification
*(Signed by the Authorized Contractor Representative upon submission — name, title, signature,
date.)* The Contractor remains fully responsible for all services performed with or through the
AI System and will, upon the City's request, maintain and provide records identifying
deliverables materially generated or modified using it.

---

## Governance posture (SOW §4)

### Bias mitigation (SOW §4.1.2)
The system operates on **project and parcel attributes** (zone, overlays, hazards, permit type,
document presence, plan text) — not on applicant demographics. Screening decisions are driven by
the transparent, City-configurable rule engine, so their basis is fully inspectable. Where AI
augments, its outputs are labeled `source = AI`, carry confidence, and are subject to staff
disposition. The governance program includes **periodic bias audits** and **fairness checks for
each significant demographic group** prior to launch and periodically thereafter, with findings
reported and mitigated.

### Accessibility (SOW §2.2.10, §2.2.11, §4.5; Appendix 3 §6.1.6, technical req 10)
The web UI conforms to **ADA and the most recent WCAG**, including **full keyboard operability
without a mouse**, adjustable time limits, no seizure-inducing content, clear
headings/labels/focus order, readable and predictable operation, input assistance/error
messaging, and compatibility with assistive technologies (screen readers, magnifiers, speech
recognition, alternative input). The frontend stack (Tailwind, semantic React components) and
vitest accessibility tests target this conformance.

### Transparency & explainability (SOW §4.2)
- **Algorithm transparency:** the primary mechanism is a documented, City-configurable rule
  engine; each finding records its **triggering condition**, assumptions, cited code reference
  (with a link where possible), and a confidence indicator.
- **Data transparency:** training-data sources for any AI component are disclosed (Exhibit 7
  §3); the regulatory knowledgebase is explicit rows in `regulatory_codes`.
- **User notification:** users are informed when interacting with an AI system and are given a
  path to interact directly with City staff (the advisory disclaimer + flag/feedback channels).

### Data minimization & US hosting (SOW §4.4.1, §4.4.3; technical reqs 14, 22)
PII is collected and used only when necessary (account identity), retained only as long as
required, and the amount of data accessed / shared with servers / shared with third parties is
minimized throughout the architecture. The AI provider receives only the data needed for the
screening task. Data is encrypted at rest and in transit, access is controlled with a zero-trust
methodology and RBAC, backups and disaster recovery are in place, and the solution and
underlying infrastructure use **U.S.-based data hosting**. When no API key is configured, the
system runs fully offline with no external model calls at all.

### Audit logging (SOW §2.2.14, §4.1.3–§4.4)
The append-only `audit_log` records every meaningful transaction — project creation, document
upload, screening trigger/complete/fail, and (by design) staff overrides and API calls — via
`AuditService`, whose writes never propagate failures so auditing cannot break the action being
audited. This provides the auditability and oversight the AI governance sections require, and
supports the analytics/KPI exports to PowerBI/SAP (SOW §2.2.12).
