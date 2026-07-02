# 09 — RBAC Design (Role-Based Access Control)

**Status: DESIGN ONLY — not yet implemented.** This document is the plan of record
for evolving the MVP's three-role model into a production RBAC that satisfies the
RFP's zero-trust and governance requirements. It doubles as input to two contract
deliverables: **4.1 Security Plan** and **4.2 Role-to-Position Mapping**.

Requirement anchors:

| Source | Requirement |
|---|---|
| SOW §2.2.15 / App. 3 §3.1.2, tech. 11 & 23 | Role-based access control; external users via Angeleno (Auth0), staff via Okta SSO |
| SOW §4.4.3 / tech. 14 | "Strict access controls … zero trust methodology and implementing role based access control" |
| App. 3 §5.1.5 | Staff review/accept/modify/reject before final disposition (a *distinct* power) |
| App. 3 §5.1.6 / UC 2.3 | Staff configure rules without vendor code changes (a *distinct* power) |
| SOW §2.2.13 | Model/rule updates "subject to City of Los Angeles review and approval" → maker-checker |
| SOW §4.1.3 / §4.2 | Human oversight, transparency, auditability of decisions and overrides |
| Deliverable 4.2 | Role-to-Position Mapping |

---

## 1. Current state (MVP baseline)

One `users.role` column with three values and a strict hierarchy
(`ADMIN ⊃ STAFF ⊃ APPLICANT`), mapped to Spring authorities in
`UserDetailsServiceImpl` and enforced by URL matchers in `SecurityConfig`
(`/api/staff/**` → STAFF, `/api/admin/**` → ADMIN). Machine clients authenticate
via `X-API-Key` (`ApiKeyAuthFilter`) and all receive the same `ROLE_API_CLIENT`.

Weaknesses this design removes:

1. **`ADMIN` is a god role.** One identity can author rules, approve its own
   changes, mint API keys, reassign roles, and read the audit log recording all
   of it. No separation of duties.
2. **`STAFF` is undifferentiated.** An LAFD clearance reviewer, a zoning plan
   checker, and a management analyst hold identical powers.
3. **API keys are all-or-nothing.** ePlanLA and a read-only dashboard consumer
   get the same `/api/v1` surface.

## 2. Design principles

- **Roles govern verbs; ownership governs nouns.** RBAC decides *what actions*
  an identity may take. Which *records* it may touch stays with resource-level
  checks (project ownership/membership), as today.
- **Roles are bundles of fine-grained permissions.** Enforcement points reference
  permissions (`finding:disposition`, `rule:publish`), never role names. Roles
  can then be re-cut administratively without touching enforcement code.
- **Scope attributes instead of role explosion.** One `CLEARANCE_REVIEWER` role
  whose *assignment* carries `department=LAFD` — not one role per department.
  Keeps the catalog at 7±2 assignable roles.
- **Admin plane ≠ content plane.** Managing the system (users, keys, config) and
  managing regulatory content (rules, corpus) are different jobs with different
  risks; no single role spans both.
- **Maker-checker for anything the engine executes.** Rule and corpus changes are
  drafted by one identity and published by another (SOW §2.2.13's
  review-and-approval, enforced structurally rather than by convention).
- **Deliberate exclusions:** no per-discipline role explosion (use scopes), no
  deny-rules/negative permissions, no role inheritance deeper than the applicant
  baseline. All three are the classic paths to unauditable RBAC.

## 3. Role catalog

### 3.1 External (Angeleno Account / Auth0 federation)

| Role | Position mapping | Permissions (indicative) |
|---|---|---|
| `APPLICANT` | Homeowners, architects, contractors, expediters | `project:create`, `project:read:own`, `project:write:own`, `document:upload:own`, `screening:execute:own`, `finding:flag`, `report:export:own` |

**Delegation is membership, not a role:** a project gains a membership table
(`owner` / `editor` / `viewer`) so a firm's staff or an owner's agent can work a
project without credential sharing. RBAC still gates the verbs; membership gates
the records.

### 3.2 City staff (Okta SSO, group-claim driven)

| Role | Position mapping | Permissions (indicative) | Scope attribute |
|---|---|---|---|
| `REVIEWER` | Plan Check Engineer I/II, intake staff | `project:read:any`, `run:read:any`, `finding:disposition`, `feedback:triage` | optional `discipline` (ZONING, FIRE, STRUCTURAL, …) limiting disposition to matching finding categories |
| `CLEARANCE_REVIEWER` | Partner-department staff (LAFD, BOE, BOS, DOT, LAHD, …) | `clearance:disposition`, `project:read:context` | **required** `department` — dispositions only for clearances of that department |
| `RULE_AUTHOR` | Knowledgebase curators, senior plan check staff | `rule:write:draft`, `kb:write:draft`, `rule:dry-run` | optional `category` |
| `RULE_APPROVER` | Supervisors, division chiefs | `rule:publish`, `rule:reject`, `kb:publish` — **self-approval structurally blocked** | — |
| `ANALYST` | Management, ED19 reporting staff | `analytics:read`, `kpi:export` (aggregates; no applicant PII beyond dashboard needs) | — |
| `AUDITOR` | City audit / AI-governance / security staff | `audit:read`, `feedback:read`, `governance:read` (model config, code-version stamps, role-grant history). **Zero write permissions.** | — |

### 3.3 Administration (deliberately split)

| Role | Position mapping | Permissions (indicative) |
|---|---|---|
| `USER_ADMIN` | LADBS system owner / designated IT | `user:role:assign`, `user:enable` — reason required, audited; **cannot self-grant** a new role family |
| `INTEGRATION_ADMIN` | IT integrations team | `apikey:create`, `apikey:revoke`, `apikey:scopes:write`, `webhook:configure` |
| `SYSTEM_ADMIN` | Vendor/City operations | `scheduler:manage`, `kb:sync:trigger`, `config:read` — **no** rule publishing, **no** role grants |

A single person may hold multiple roles where the City chooses (small teams), but
the *policy* constraints (no self-approval, no self-grant) hold regardless.

### 3.4 Machine principals — scoped API keys (not user roles)

| Scope set | Example holder |
|---|---|
| `projects:write screenings:execute results:read` | ePlanLA / LACPS full integration |
| `results:read` | Departmental dashboard that only pulls outcomes |
| `kb:read mcp:invoke` | MCP consumers (parcel / permit-type / code lookup) |
| `webhooks:receive` | Registered callback endpoints |

Plus per-key **expiry/rotation dates** and per-key **rate limits**. Existing
hygiene is retained: SHA-256-only storage, show-once, revocation, per-key audit
attribution.

## 4. Administration workflows

### 4.1 Assignment provenance — IdP first, local last

Production role assignment derives from identity-provider claims:

- **Okta groups → roles** through an admin-editable *group→role mapping table*
  (e.g. `LADBS-PlanCheck-Zoning` → `REVIEWER{discipline=ZONING}`).
- **Angeleno (Auth0)** users always map to `APPLICANT`; external identities can
  never acquire staff roles through federation.
- **Local DB grants are break-glass only**: visually flagged in the admin UI,
  reason-required, auto-expiring, audited.

Joiner/mover/leaver is then automatic — HR removes the Okta group and the access
dies with it. No orphaned admin accounts.

### 4.2 Role-to-Position Mapping as a living artifact (deliverable 4.2)

The admin UI renders the actual runtime matrix — *Position → IdP group → role
(+scope) → permissions* — so the compliance document and the running
configuration are the same thing and cannot drift.

### 4.3 Maker-checker for rules & corpus (SOW §2.2.13)

`screening_rules`, `clearance_rules`, and `regulatory_codes` gain a lifecycle:

```
DRAFT ──submit──▶ PENDING_APPROVAL ──publish (RULE_APPROVER ≠ author)──▶ PUBLISHED
  ▲                     │reject                                             │
  └─────────────────────┘                        engine executes ONLY PUBLISHED
```

The approval queue shows the **diff** (old vs. new condition), the author, and a
**dry-run preview** ("this change would have altered N of the last 100 runs").
This converts today's audit-*after* model into review-*before*.

### 4.4 Access recertification

A quarterly report generated from role tables + audit log: every privileged-role
holder, grant date, grantor, last activity. `AUDITOR` reads; `USER_ADMIN` acts.
Dormant privileged accounts auto-disable.

### 4.5 Effective-permissions view

The user-detail admin page answers "why does this user have this?" — effective
permissions with provenance (which IdP group / role / scope granted each). Most
operational RBAC pain is diagnosing access, not granting it.

## 5. Enforcement architecture

- Keep coarse URL matchers (`/api/staff/**`, `/api/admin/**`) as the outer wall.
- Real decisions move to **method-level permission checks**
  (`@PreAuthorize("hasAuthority('rule:publish')")`) so services are safe
  regardless of the controller that reaches them.
- The one contained piece of attribute logic: scoped checks like
  `clearance.department ∈ user.scopes(DEPARTMENT)` for `CLEARANCE_REVIEWER`,
  implemented in a single authorization helper, not scattered.

### 5.1 Schema sketch

```sql
-- Many roles per user, optionally scoped; full grant provenance.
CREATE TABLE role_assignments (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        varchar(32)  NOT NULL,          -- catalog value
    scope_type  varchar(32),                    -- DEPARTMENT | DISCIPLINE | NULL
    scope_value varchar(64),
    source      varchar(16)  NOT NULL,          -- IDP | LOCAL (break-glass)
    granted_by  uuid,
    reason      text,
    expires_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- Role → permission bundles (seed-managed, admin-visible).
CREATE TABLE role_permissions (
    role        varchar(32) NOT NULL,
    permission  varchar(64) NOT NULL,
    PRIMARY KEY (role, permission)
);

-- IdP group claims → role(+scope) mapping (USER_ADMIN-editable).
CREATE TABLE idp_group_mappings (
    idp_group   varchar(128) PRIMARY KEY,
    role        varchar(32)  NOT NULL,
    scope_type  varchar(32),
    scope_value varchar(64)
);

CREATE TABLE api_client_scopes (
    client_id   uuid NOT NULL REFERENCES api_clients(id) ON DELETE CASCADE,
    scope       varchar(64) NOT NULL,
    PRIMARY KEY (client_id, scope)
);

-- Rule/corpus lifecycle for maker-checker (per rule table):
--   ALTER TABLE screening_rules ADD COLUMN lifecycle varchar(24)
--     NOT NULL DEFAULT 'PUBLISHED';   -- DRAFT | PENDING_APPROVAL | PUBLISHED
--   + draft_of uuid (self-FK), submitted_by, approved_by, approved_at.
```

`users.role` survives as a computed "primary profile" for UI display; the
authorities served to Spring Security become the union of assigned roles'
permissions.

## 6. Migration plan (from the MVP model)

| Step | Change | Risk |
|---|---|---|
| 1 | Introduce permission constants + `@PreAuthorize` on sensitive endpoints (dispositions, rule writes, key management). Roles still map 1:1 to today's three bundles — zero behavior change. | Low |
| 2 | Add `role_assignments` (multi-role + scopes); `STAFF`/`ADMIN` become compatibility bundles. | Low-medium |
| 3 | Split `ADMIN` → `USER_ADMIN` / `INTEGRATION_ADMIN` / `SYSTEM_ADMIN`; add `RULE_AUTHOR`/`RULE_APPROVER` with the DRAFT→PUBLISHED lifecycle (largest piece — touches rule tables and the engine's `findByActiveTrue…` queries, which become `findByActiveTrueAndLifecyclePublished…`). | Medium |
| 4 | API-key scopes (`api_client_scopes` + scope checks in `/api/v1` + MCP). | Low-medium |
| 5 | IdP group mapping when Auth0/Okta federation lands (subsumes step 2's assignment source; local grants become break-glass). | Medium (external dependency) |

Each step is independently shippable and testable; step 1 is immediately useful
and carries essentially no risk.

## 7. Threats this design addresses

| Threat | Mitigation |
|---|---|
| Rogue/compromised admin rewrites rules silently | Admin/content plane split + maker-checker + audit |
| Self-approval of rule changes | `RULE_APPROVER ≠ author` enforced structurally |
| Privilege creep over time | Recertification report + auto-expiry + IdP-driven leaver flow |
| Cross-department data access | `CLEARANCE_REVIEWER` department scoping |
| Over-privileged integrations | Per-key scopes, expiry, rate limits |
| "Why can this user do X?" opacity | Effective-permissions view with provenance |
