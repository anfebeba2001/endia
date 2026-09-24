# Data Model: Custom Keyword "Define" from a Held Text Selection (2-Second Trigger)

**Feature**: `016-custom-keyword-define`

**Persistence**: **NO schema change. NO entity change. NO migration.** The generated definition is
produced on demand, returned in the endpoint response and shown transiently in the existing
definition card. Nothing is persisted (spec §Project Structure Impact, Clarification #6, Non-Goals).
The project has **no Flyway/Liquibase**; schema is managed by Hibernate
`spring.jpa.hibernate.ddl-auto=update` (`backend/src/main/resources/application.yml`).
Because no entity or column changes, `ddl-auto=update` issues **no DDL** for this feature.

This document therefore records the **in-flight value types** (the backend provider record, the API
DTOs, the service result) and the **frontend UI value types** (the selected term, the generated
definition response, the card view model) rather than any stored model.

---

## 1. Domain / provider value type (backend)

### 1.1 `LlmProvider.GeneratedDefinition` (new, provider-agnostic)

`com.endia.application.port.LlmProvider` — a pure record on the existing port, mirroring
`GeneratedSnack`/`GeneratedMeaningSnack`:

```java
/** Provider-agnostic result of a short-definition generation (feature 016, ≤20 words). */
record GeneratedDefinition(String definition) {}
```

| Aspect | Detail |
|--------|--------|
| Fields | `definition` — the model's short meaning for the term. |
| Type | Plain, untrusted generated text. Never HTML; escaping happens at render time only. |
| Bound | Validated/truncated at the service boundary to at most **20** whitespace-separated tokens (FR-004). |
| Persistence | None. A transient provider result, never stored. |

No new domain **entity** is introduced: the feature has no persisted aggregate.

---

## 2. Application service result (backend)

### 2.1 `DefinitionService.DefinitionResult` (new)

`com.endia.application.service.DefinitionService`:

```java
public DefinitionResult generate(UUID themeId, String term);

public record DefinitionResult(String term, String definition) {}
```

| Field | Type | Notes |
|-------|------|-------|
| `term` | `String` | The **normalized** selected term (trimmed, whitespace-collapsed), ≤10 whitespace tokens. Echoed to the client. |
| `definition` | `String` | The validated/normalized generated definition, non-blank, ≤20 whitespace tokens. |

The service is the single normalization/validation boundary (constitution IV): it rejects a blank or
oversized term **before** any LLM call (FR-010/FR-011) and bounds the generated definition (FR-004).

---

## 3. API value types (backend DTOs)

### 3.1 Request — `DefinitionGenerateApiRequest` (new)

`com.endia.interfaces.rest.dto.DefinitionGenerateApiRequest`:

```java
public record DefinitionGenerateApiRequest(
        @NotNull(message = "themeId is required") UUID themeId,
        @NotBlank(message = "term must not be blank") String term) {}
```

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `themeId` | `UUID` | yes | Must resolve to an existing `Theme` (else 404 `NOT_FOUND`); supplies the prompt context. |
| `term` | `string` | yes | Non-blank after trim (bean validation → 400); normalized and bounded to ≤10 whitespace-separated tokens at the service boundary (else 400 `VALIDATION_ERROR`). |

`subtopicId` is deliberately **absent** (a term's definition is theme-scoped; the selected term may
be inside a meaning-snack card with no quiz subtopic). See research R1 / plan.md Open Decisions #1.

### 3.2 Response — `DefinitionGenerateApiResponse` (new)

`com.endia.interfaces.rest.dto.DefinitionGenerateApiResponse`:

```java
public record DefinitionGenerateApiResponse(String term, String definition) {}
```

| Field | Type | Notes |
|-------|------|-------|
| `term` | `String` | The normalized selected term. |
| `definition` | `String` | The generated short definition (≤20 words). |

Exactly two fields. No `id`, no `kind`, no persisted-entity fields, no quiz fields, no `keywords`.

### 3.3 Error envelope (unchanged)

Errors reuse the existing `GlobalExceptionHandler.ErrorResponse` shape:
`{ "error": string, "message": string, "field": string|null, "cause": string|null }`.

| Status | `error` | When |
|--------|---------|------|
| 400 | `VALIDATION_ERROR` | Missing/blank `term` (bean validation), or a term exceeding 10 whitespace tokens (`InvalidSelectionException`). |
| 404 | `NOT_FOUND` | `themeId` does not exist. |
| 503 | `SERVICE_UNAVAILABLE` | LLM unavailable, or the generated definition still blank after one re-roll. |
| 500 | `INTERNAL_ERROR` | Unexpected server error. |

The new `InvalidSelectionException` (domain/exception) carries `(400, "VALIDATION_ERROR", message)`
and is mapped by the existing handler with **no handler change** (mirrors `InvalidOptionIndexException`).

---

## 4. Storage impact (explicit: none)

| Item | Detail |
|------|--------|
| New table | **None.** |
| Altered table/column/index | **None.** |
| Migration | **None required.** No entity/column change, so `ddl-auto=update` issues no DDL. |
| Rollback | Revert the source edits (prompt, port/provider, service, exception, DTOs, controller, tests, frontend) and redeploy. **No data change**, so rollback is lossless and requires no SQL. |
| Pre-existing data | `theme`, `subtopic`, `snack`, `quiz_question`, `meaning_snack`, `study_session*` are untouched and remain valid. |

---

## 5. Frontend UI value types

All new types are additive and in-memory; none is persisted.

### 5.1 Selected term (UI concept, not a stored model)

The text the user selects inside a snack's reading content and holds ~2s. Read as **plain text** via
`Selection.toString()` and normalized to `trim()` + whitespace-collapse, bounded to ≤10
whitespace-separated tokens. Untrusted; never rendered by the directive.

```ts
// Emitted by SelectionDefineDirective when the ~2s hold completes.
interface TermSelectedEvent {
  term: string;   // normalized selected text (plain, untrusted)
  rect: DOMRect;  // selection bounding box, for anchoring the affordance
}
```

### 5.2 Generated definition response

```ts
// models/definition.model.ts
export interface DefinitionGenerateResponse {
  term: string;       // normalized selected term
  definition: string; // generated short definition (≤20 words, untrusted plain text)
}
```

### 5.3 Card view model (existing, reused)

The generated definition is fed into the existing card as a `SnackKeyword`:

```ts
// models/snack.model.ts (existing, unchanged)
export interface SnackKeyword {
  term: string;
  definition: string;
}
```

| Card state | `activeKeyword` | `definitionLoading` | `definitionError` |
|------------|-----------------|---------------------|-------------------|
| Loading | `{ term, definition: '' }` | `true` | `null` |
| Success | `{ term, definition: generated }` | `false` | `null` |
| Failure | `{ term, definition: '' }` | `false` | user-safe message |

On failure the card shows the term and the error and renders **no** definition paragraph (nothing
empty/placeholder is added — FR-009). On success the card renders the definition via escaped
interpolation only.

### 5.4 New card inputs (additive)

```ts
// KeywordDefinitionCardComponent (additive; defaults are no-ops so 013/014 are unchanged)
readonly definitionLoading = input<boolean>(false);
readonly definitionError = input<string | null>(null);
readonly anchorRect = input<DOMRect | null>(null); // positioning when `anchor` is null
```

---

## 6. Key entities (spec cross-reference)

- **SelectedTerm** (UI concept) → §5.1: normalized (trim + collapse), ≤10 whitespace tokens,
  untrusted; blank/oversized rejected before any request.
- **GeneratedDefinition** (new, provider-agnostic) → §1.1/§2.1/§3.2: a short definition ≤20 words
  produced through the externalized LLM flow; untrusted; **not persisted**.
- **"Define" affordance** (UI concept) → §5.1/§5.3: the transient, selection-anchored control shown
  after the ~2s hold and dismissed when the selection changes/clears.
- **Definition card** (existing, reused) → §5.3/§5.4: the single card that shows the term and its
  meaning; opened by the page and still governed by the single-card rule.
- **Hold trigger** (concept) → §5.1: the ~2s timer in `SelectionDefineDirective`, cancelled on
  selection change/clear.
- **No-persistence invariant** → §4: nothing is stored; no schema change.
