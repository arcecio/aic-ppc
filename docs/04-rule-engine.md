# 04 — Configurable Rule Engine

City of Los Angeles / LADBS — **AI-Powered Pre-Plan Check Assistant** (RFP 2025AIP007)

The rule engine is the **primary** detection mechanism (SOW §2.2.3: "configurable rule-based
logic … as the primary mechanism with AI-assisted analysis used to enhance detection"). It is
deliberately **data-driven**: rules live as rows in `screening_rules` and `clearance_rules`,
each carrying a JSON boolean condition tree. City staff add, edit, disable, and re-order rules
**without any vendor code change** (SOW §2.1.6, §2.2.3; Appendix 3 §5.1.6, technical req 8).

The evaluator itself is
`backend/src/main/java/com/lacity/aipppc/service/rules/RuleConditionEvaluator.java`. The
evaluation context is built by
`backend/src/main/java/com/lacity/aipppc/service/screening/ProjectContextBuilder.java`.
Placeholder rendering is `service/screening/TemplateRenderer.java`.

---

## The condition grammar

A rule's `conditionJson` is a JSON tree of **nodes**. There are four node types:

| Node | Shape | Semantics |
|---|---|---|
| **all** (AND) | `{"all": [node, node, ...]}` | True iff every child is true (short-circuits on first false) |
| **any** (OR) | `{"any": [node, node, ...]}` | True iff at least one child is true (short-circuits on first true) |
| **not** | `{"not": node}` | Logical negation of one child |
| **leaf** | `{"field": F, "op": OP, "value": V}` | Compares context field `F` against `V` using operator `OP` |

Nodes nest arbitrarily. Evaluation is recursive (`evaluate(JsonNode, ctx)`), and the whole
tree is parsed with Jackson on each run.

**Fail-safe behavior:** any malformed node — an unrecognized node shape, an unknown operator,
or a JSON parse error — evaluates to `false` and is logged as a warning. A bad staff edit can
never crash a screening run; the worst case is that a single rule silently does not fire. This
is why `matches()` returns `false` on a blank or unparseable condition.

### Leaf operators

The leaf `op` is matched case-insensitively (`toLowerCase`). Supported operators, exactly as
implemented in `evaluateLeaf()`:

| Operator | Meaning | Notes |
|---|---|---|
| `eq` | equals | Numeric if both sides numeric; boolean-aware; otherwise case-insensitive string compare. **Default op when `op` is omitted.** |
| `neq` | not equals | Negation of `eq` |
| `in` | value is a member of an array | `value` must be a JSON array; each item compared like `eq` (numeric or case-insensitive text) |
| `contains` | substring / list membership | If the field is a **list**, tests membership; if a **string**, tests case-insensitive substring. `value` is a scalar |
| `containsAny` | intersection / any-substring | `value` is an **array**. List field → any element matches; string field → any value is a substring |
| `gt` | greater than | Numeric. Missing/non-numeric field → false |
| `gte` | greater than or equal | Numeric. Missing/non-numeric field → false |
| `lt` | less than | Numeric. **Missing/non-numeric field → false** (does not fire on absent data) |
| `lte` | less than or equal | Numeric. Missing/non-numeric field → false |
| `exists` | field is present and non-empty | Non-null; non-blank string; non-empty list |
| `missing` | field is absent or empty | Logical inverse of `exists` |
| `regex` | case-insensitive regex `find` | `Pattern.CASE_INSENSITIVE`; substring match (`find`, not full-match) |

Numeric comparisons are done in `double`; strings that parse as numbers are coerced. The
asymmetry between `gt`/`gte` (which treat a missing field as "false" via a `-1` sentinel) and
`lt`/`lte` (which use `safeCompare` and return `false` when either side is non-numeric) is
intentional: a `lt` threshold rule must **not** fire just because the applicant left a numeric
field blank.

Equality coercion (`equalsScalar`): if the actual value is a `Number` and the JSON value is
numeric, they're compared with `Double.compare`; a `Boolean` actual is compared against
`value.asBoolean()`; otherwise both are compared as case-insensitive strings.

---

## The evaluation context (fields available to rules)

`ProjectContextBuilder.build(project, permitType, documents, combinedDocText)` flattens the
project into a single string-keyed `Map<String,Object>`. Every field below is a valid `field`
in a leaf node. Rule authors reference these keys.

| Field | Type | Source |
|---|---|---|
| `permitType` | string | `project.permitTypeCode` (e.g. `SFD_NEW`) |
| `permitCategory` | string | `permitType.category.name()` (`RESIDENTIAL`/`MULTI_FAMILY`/`COMMERCIAL`/`OTHER`), or `OTHER` |
| `scope` | string (lower-cased) | `project.projectScope` |
| `intendedUse` | string (lower-cased) | `project.intendedUse` |
| `description` | string (lower-cased) | `project.description` |
| `hasParcel` | boolean | Whether a parcel was resolved |
| `zone` | string | `parcel.zone` (null if no parcel) |
| `overlays` | list<string> | `parcel.overlaysJson` (empty list if no parcel) |
| `hazards` | list<string> | `parcel.hazardZonesJson` (empty list if no parcel) |
| `councilDistrict` | integer | `parcel.councilDistrict` |
| `communityPlanArea` | string | `parcel.communityPlanArea` |
| *(dynamic form fields)* | any | Each key in `project.formDataJson` is added with `putIfAbsent` (e.g. `stories`, `units`, `squareFootage`, `frontYardSetbackFt`, `gradingCubicYards`, `includesAffordable`, `parkingSpaces`, `occupancyGroup`, `changeOfUse`, `signAreaSqFt`, `occupantLoad`) |
| `presentDocs` | list<string> | `docCategory` of every scan-**passed** document (deduped) |
| `missingDocs` | list<string> | Required doc keys from the permit type not present |
| `text` | string (lower-cased) | Concatenation of title + scope + intended use + description + **all extracted document text** — the field keyword rules match against |

Notes:
- Because dynamic form answers are added with `putIfAbsent`, a permit-type field **cannot
  shadow** a built-in context key (`zone`, `overlays`, etc.).
- `text` is the workhorse for content-based rules — it lets a rule fire on words appearing in
  the applicant's narrative *or* inside the uploaded plans (extracted by PDFBox / POI).
- `missingDocs` is computed by `missingRequired(permitType, present)`, which reads the permit
  type's `requiredDocsJson` and returns every `docKey` marked `required: true` that isn't in
  `presentDocs`.

---

## Placeholder rendering (`{{field}}`)

Rule `message`, `recommendation`, and clearance `reason` strings may contain
`{{placeholder}}` tokens. When a finding/clearance is created, `TemplateRenderer.render()`
substitutes each placeholder with the corresponding context value:

- Pattern: `\{\{\s*([a-zA-Z0-9_]+)\s*}}` (optional surrounding whitespace tolerated).
- A **null** value renders as `(unspecified)`.
- A **list** renders as a comma-separated join (`overlays` → `Hillside, Coastal Zone`).
- A **whole-number double** renders without the decimal (`15.0` → `15`).
- Everything else uses `String.valueOf`.

So a rule message of
`"The proposed front yard setback appears to be {{frontYardSetbackFt}} ft, but R1 zones
({{zone}}) generally require a 15 ft front yard"` renders concrete numbers in the finding the
applicant sees.

---

## How rules become findings / clearances

Inside `ScreeningService.runScreening()`:

**Screening rules** are fetched via `findByActiveTrueOrderByPriorityAsc()` — only active
rules, ordered by ascending `priority`. For each rule:
1. `appliesTo(rule.appliesToPermitTypes, permitTypeCode)` — the rule's CSV of permit type
   codes must include the project's type, or be blank / `*` (all types). Matching is
   case-insensitive per-token.
2. `evaluator.matches(rule.conditionJson, ctx)` — the condition tree must evaluate true.
3. If both pass, a `Finding` is built carrying the rule's category, severity, code
   reference/URL, and baseline confidence; the `message` and `recommendation` are run through
   `TemplateRenderer`; `source = RULE`, `ruleCode = rule.code`, and `triggeringCondition`
   records `"Rule <code> matched the submission."`.

**Clearance rules** run the same way (`findByActiveTrueOrderByPriorityAsc()`,
`appliesTo`, `matches`) and produce a `Clearance` with department, rendered reason,
confidence, submittal requirements JSON, and info URL.

### Review-sequence priority ordering (SOW §2.1.3)

`priority` implements the RFP's **review-sequence logic** — "evaluate foundational
constraints (zoning, use, parcel) before detailed architectural/structural components." Lower
`priority` runs earlier. In the seed data this is used to front-load high-impact zoning/site
constraints:

| Priority band | Example seed rules |
|---|---|
| 15–20 | `COASTAL-CDP` (15), `ZON-HIGHRISE-REVIEW` (20), `FIRE-VHFHSZ` (20), clearance `CLR-PLANNING-COASTAL` (15) |
| 25–30 | `ZON-HILLSIDE-GRADING` (25), `HAZ-METHANE` (25), `ZON-SETBACK-FRONT` (30), `HAZ-LIQUEFACTION-SOILS` (30) |
| 35–45 | `ACC-COMMERCIAL-ROUTE` (35), `GRN-CALGREEN-NEW` (40), `SIGN-AREA-LIMIT` (40), `ACC-OCCLOAD-RESTROOM` (45) |
| 50–65 | `STR-CALCS-EXPECTED` (50), `PARK-AB2097-TRANSIT` (55), `ZON-FAR-COVERAGE` (60), clearance `CLR-BSS-STREET-TREE` (65) |

Because findings are also **re-sorted for display** by severity then confidence
(`PreCheckService.toDetail`), `priority` primarily governs *evaluation* order, not final
presentation order.

---

## How staff edit rules without code changes

Rules are plain database rows. The intended editing paths:

1. **Seed corpus** — `ReferenceDataSeeder` loads `seed/screening-rules.json` and
   `seed/clearance-rules.json` on boot. Seeding is **idempotent by `code`**: an existing rule
   is left untouched, so staff edits are never clobbered on restart.
2. **Admin rule-management API** (implemented) — the `/api/admin/**` surface (ADMIN role, per
   `SecurityConfig`) is where staff CRUD rules at runtime, via `AdminController` +
   `RuleAdminService` (`GET/POST/PUT/DELETE /api/admin/screening-rules` and
   `.../clearance-rules`) with the `AdminRules.tsx` staff UI on top. Editing a rule is editing its
   `conditionJson`, `severity`, `message`, `recommendation`, `codeReference`, `confidence`,
   `appliesToPermitTypes`, `priority`, or `active` flag — no deployment required.

Every rule change is expected to be captured in the `audit_log` (SOW §2.2.13 requires all
learning/tuning to be transparent and auditable, and model/rule updates to be subject to City
review). Staff QA of the resulting findings/clearances runs through `StaffDisposition`
(accept / modify / reject) on each individual finding.

---

## Worked examples from the seed

The examples below are taken verbatim from `seed/screening-rules.json` and
`seed/clearance-rules.json`.

### 1. Nested `all` with a numeric threshold and a substring — `ZON-SETBACK-FRONT`

```json
{
  "code": "ZON-SETBACK-FRONT",
  "category": "ZONING",
  "severity": "WARNING",
  "condition": {
    "all": [
      { "field": "frontYardSetbackFt", "op": "lt", "value": 15 },
      { "field": "zone", "op": "contains", "value": "R1" }
    ]
  },
  "message": "The proposed front yard setback appears to be {{frontYardSetbackFt}} ft, but R1 zones ({{zone}}) generally require a 15 ft front yard (LAMC 12.08-C).",
  "recommendation": "Increase the front yard setback to at least 15 ft, or provide documentation of an approved adjustment, variance, or averaging determination.",
  "codeReference": "LAMC 12.08-C",
  "confidence": 80,
  "appliesToPermitTypes": "SFD_NEW,SFD_ADDITION,ADU",
  "priority": 30
}
```
Fires when the applicant's `frontYardSetbackFt` form answer is a number below 15 **and** the
resolved parcel `zone` string contains `R1`. Note the `lt` operator will not fire if the
setback field was left blank.

### 2. `containsAny` on a parcel list — `FIRE-VHFHSZ`

```json
{
  "code": "FIRE-VHFHSZ",
  "category": "FIRE",
  "severity": "WARNING",
  "condition": {
    "field": "overlays",
    "op": "containsAny",
    "value": ["Very High Fire Hazard Severity Zone"]
  },
  "codeReference": "LAMC 57.322 / CBC Ch.7A",
  "confidence": 90,
  "priority": 20
}
```
`overlays` is a list; `containsAny` returns true if any of the listed values is a member. A
single-element `value` array behaves as "list contains this value."

### 3. `not` wrapping a `contains`, combined with `in` — `ACC-COMMERCIAL-ROUTE`

```json
{
  "code": "ACC-COMMERCIAL-ROUTE",
  "category": "ACCESSIBILITY",
  "severity": "WARNING",
  "condition": {
    "all": [
      { "field": "permitCategory", "op": "in", "value": ["COMMERCIAL"] },
      { "not": { "field": "presentDocs", "op": "contains", "value": "accessibility_plans" } }
    ]
  },
  "appliesToPermitTypes": "COMMERCIAL_NEW,COMMERCIAL_TI",
  "priority": 35
}
```
Fires for commercial projects that have **not** uploaded a document tagged
`accessibility_plans` — a completeness-adjacent zoning/accessibility check expressed purely
in the rule grammar.

### 4. `any` combining a form flag with keyword matching on `text` — `ZON-CHANGE-OF-USE`

```json
{
  "code": "ZON-CHANGE-OF-USE",
  "condition": {
    "any": [
      { "field": "changeOfUse", "op": "eq", "value": true },
      { "field": "text", "op": "containsAny", "value": ["change of use", "change in use", "convert to"] }
    ]
  }
}
```
Fires either because the applicant checked the `changeOfUse` boolean **or** because the
combined narrative/plan text mentions any of the phrases — the same rule catches a structured
answer and free-text evidence.

### 5. Numeric `gte` with a list intersection — clearance `CLR-BOS-SWPPP-LID`

```json
{
  "code": "CLR-BOS-SWPPP-LID",
  "department": "BOS",
  "clearanceName": "Bureau of Sanitation — Stormwater / SWPPP / LID",
  "condition": {
    "any": [
      { "field": "gradingCubicYards", "op": "gte", "value": 500 },
      { "field": "squareFootage", "op": "gte", "value": 10000 },
      { "field": "permitType", "op": "in", "value": ["MULTIFAMILY_NEW", "COMMERCIAL_NEW"] },
      { "field": "text", "op": "containsAny", "value": ["stormwater", "swppp", "low impact development", "lid", "grading", "impervious"] }
    ]
  },
  "submittalRequirements": [
    "Low Impact Development (LID) plan",
    "SWPPP / erosion and sediment control plan",
    "Stormwater best management practices summary"
  ],
  "confidence": 68,
  "priority": 50
}
```
A four-way `any`: significant grading quantity, large area, priority permit type, or stormwater
keywords each independently trigger the Bureau of Sanitation clearance. `submittalRequirements`
becomes the clearance's document checklist shown to the applicant.

### 6. Placeholder rendering with a list — clearance `CLR-LADBS-GRADING`

```json
{
  "reason": "Hillside conditions, seismic hazards, or grading quantities ({{gradingCubicYards}} cy) require LADBS Grading Division review of soils/geology and grading plans (LAMC 91.7006)."
}
```
`{{gradingCubicYards}}` renders the numeric form answer (or `(unspecified)` if absent). Had the
placeholder referenced `{{hazards}}`, the renderer would produce `Liquefaction, Fault`.

---

## Seed rule inventory (as shipped)

The seed ships **25 screening rules** and **15 clearance rules**:

- **Screening rules** span zoning/site (setbacks, height/stories, FAR/coverage, hillside
  grading, coastal, HPOZ, TOC, AB 2097 parking, change of use, signs), fire (VHFHSZ), hazards
  (methane, liquefaction/soils), building (occupancy classification, mixed-occupancy
  separation, construction type/allowable area, high-rise review), accessibility (commercial
  route, multi-family units, occupant-load restrooms), structural (calcs expected), green
  (CALGreen, Title 24 energy), and electrical (EV-ready).
- **Clearance rules** cover City Planning (entitlement, coastal CDP, HPOZ, density-bonus/TOC),
  LAFD (life safety, hazmat/brush), BOE (right-of-way/B-permit, sewer), BOS (SWPPP/LID), BSS
  (street tree), BCA (prevailing wage), LAHD (RSO), LADWP (water & power), DOT
  (driveway/traffic), and LADBS (grading/geology).

Because both rule sets are pure data, expanding coverage — adding disciplines, thresholds, or
whole new departments — is a data task for City staff, not a code change.
