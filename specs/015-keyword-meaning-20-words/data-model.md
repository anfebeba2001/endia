# Data Model: Expand Keyword Meaning Limit from 3 to 20 Words

**Feature**: `015-keyword-meaning-20-words`

**Persistence**: **NO schema change. NO entity change. NO migration.** This feature changes only a
length-policy constant, prompt wording and documentation/comments. Definitions are already stored as
free text/JSON and previously stored 3-word definitions remain valid and are served unchanged
(FR-017, SC-007).

The project has **no Flyway/Liquibase**; schema is managed by Hibernate
`spring.jpa.hibernate.ddl-auto=update` (`backend/src/main/resources/application.yml`). Because no
entity or column changes, `ddl-auto=update` issues **no DDL** for this feature.

---

## 1. Existing fields in scope (documented, not modified)

### 1.1 `SnackKeyword.definition` (feature 013)

`com.endia.domain.model.SnackKeyword` — a pure domain value object:

```java
public record SnackKeyword(String term, String definition) { ... }
```

| Aspect | Detail |
|--------|--------|
| Storage | Serialized inside `Snack.keywords`, a `JSON` column (`@Column(nullable = true, columnDefinition = "JSON")`) via `com.endia.infrastructure.persistence.JpaSnackKeywordListConverter`. **No per-definition DB column and no DB length constraint.** |
| Type | Plain, untrusted text. Never HTML; escaping happens at render time only. |
| New policy | At most **20** whitespace-separated tokens (previously 3). Over-limit → truncated to the first 20 by `SnackService.normalizeKeywords` before persist/serve. |
| Old values | Previously stored 3-word definitions remain valid; no migration, no re-normalization on read. |
| Comment surface | Javadoc in `SnackKeyword` ("at most 3 words") and `SnackKeywordDto` ("at most 3 words, guaranteed by service normalization") updated to 20. |

### 1.2 Meaning-snack source definition (feature 014)

`com.endia.domain.model.MeaningSnack`:

| Field | Column | Type | Notes |
|-------|--------|------|-------|
| `sourceTerm` | `source_term` | `VARCHAR(500)` | The keyword `term`; unchanged. |
| `sourceDefinition` | `source_definition` | **`VARCHAR(200)`** | A copy of the keyword `definition`, normalized at the service boundary by `MeaningSnackService.normalizeDefinition`. New policy: at most **20** whitespace-separated tokens (previously 3), **and** at most **200 characters on a word boundary** (FR-019). |

`sourceDefinition` is the only place where a longer cap meets a concrete storage bound. It is handled
without a schema change by an additional character clamp — see §2.1 and §4.

### 1.3 UI concept — definition card

The dismissible card/popover in `frontend/src/app/components/keyword-definition-card/` renders
`term` + `definition` with escaped interpolation only. It is **not** a persisted model; it is listed
here because its readability at up-to-20 words is a requirement (FR-011..FR-014) verified, not
redesigned.

---

## 2. The 20-token policy (single normalization rule, two call sites)

The rule is identical to the previous one with the bound raised, plus a character clamp that applies
**only** to the meaning-snack source definition (decision (a) / FR-019):

```
normalizeDefinition(raw):                  # used by MeaningSnackService (source definition)
    trimmed = raw.trim()
    tokens  = trimmed.split("\\s+")
    if tokens.length > 20:                 # inclusive bound: 20 is valid
        trimmed = join(" ", tokens[0..20))
    if trimmed.length > 200:               # FR-019: fit VARCHAR(200), never split a word
        cut = trimmed.lastIndexOf(' ', 200)
        trimmed = cut > 0 ? trimmed[0..cut) : trimmed[0..200)
    return trimmed

normalizeKeywords(rawList):                # used by SnackService (snack keywords)
    for each keyword:
        term       = keyword.term.trim()          # blank term  -> drop
        definition = keyword.definition.trim()
        if definition.split("\\s+").length > 20:
            definition = join(" ", tokens[0..20)) # token cap only; JSON column, no char clamp
        if definition is blank -> drop
        de-duplicate terms case-insensitively, first-wins, order-preserving
```

| Rule | Behaviour | Source |
|------|-----------|--------|
| Bound (both) | At most **20** whitespace-separated tokens, inclusive. | FR-001, FR-005 |
| Over-limit (both) | Truncate to the first 20 tokens (never reject, never re-roll). | FR-002, FR-003 |
| Exactly 19 / 20 | Kept in full. | FR-002, US2 |
| Exactly 21+ | Truncated to exactly the first 20. | FR-002, US2 |
| **Char clamp (source only)** | The meaning-snack `sourceDefinition` is further clamped to **≤200 characters on a word boundary** (never splits a word; single token >200 → hard 200-char cut). | FR-019 |
| **Effective source bound** | `min(20 tokens, ≤200 chars on a word boundary)`. | FR-019 |
| Blank/empty/whitespace-only | Still dropped (unchanged). | FR-006 |
| Trim / de-dup / order | Unchanged (case-insensitive, first-wins, order-preserving). | FR-006 |
| Hyphenated compounds | Count as one token (whitespace split only). | FR-005 |
| Punctuation | Does not split tokens. | FR-005 |
| Applies only to | Keyword `definition` and meaning-snack `sourceDefinition`. | FR-007 |
| Does **not** apply to | `concept`, `metaphor`, `whyItMatters`, the `related` 2–3 **item** cap, the 2–5 keyword-count guideline. | FR-007, Non-Goals |

**Implementation**: the token bound is a private constant in each service
(`private static final int MAX_DEFINITION_TOKENS = 20;`); the char clamp is a private constant in
`MeaningSnackService` only (`private static final int MAX_SOURCE_DEFINITION_CHARS = 200;`)
(research.md R2/R6). The literal `20`/`200` is an acceptable minimal-diff alternative.
`SnackService.normalizeKeywords` does **not** apply the char clamp — `SnackKeyword.definition` is
stored in the `snack.keywords` JSON column and is not character-constrained.

---

## 3. API representation (unchanged shapes)

Keyword definitions are carried in existing response DTOs, all of which embed `SnackKeywordDto`:

| DTO | Where | Change |
|-----|-------|--------|
| `SnackGenerateApiResponse.SnackItem` | `POST /api/snacks/generate` | Shape unchanged; `keywords[].definition` may now contain up to 20 tokens. |
| `BatchStatusApiResponse.SnackDto` | batch status (backbone source) | Shape unchanged. |
| `QuizSummaryApiResponse` | results recap | Shape unchanged. |
| `ThemeHistoryApiResponse` | read-only history | Shape unchanged. |
| `MeaningSnackItem` | `POST /api/meaning-snacks/generate`, `GET /api/meaning-snacks` | Shape unchanged; `sourceDefinition` may now contain up to 20 tokens and is guaranteed ≤200 characters (FR-019). |

**No DTO field is added, removed, renamed or re-typed.** Only the *content length* of the `definition`
/ `sourceDefinition` string values changes. Contract details are in
`contracts/keyword-definition-length-contract.md`.

---

## 4. Storage bound — `source_definition VARCHAR(200)` (resolved, decision (a) / FR-019)

| Item | Detail |
|------|--------|
| Column | `meaning_snack.source_definition VARCHAR(200) NOT NULL` (`@Column(length = 200)`) — **unchanged**. |
| Concern | 20 average words (≈8 chars + a space) is ≈180 chars and fits. 20 **long** words could exceed 200 characters; under MySQL strict mode the insert would fail (not silently truncate). |
| Spec constraint | FR-017 / Assumptions mandate **no schema change** and **no migration**. |
| Resolution (option (a), product-owner approved) | Keep `VARCHAR(200)`; `MeaningSnackService.normalizeDefinition` clamps the normalized source definition to **≤200 characters on a word boundary** (never splitting a word) after applying the 20-token cap. The column can therefore never be overrun, so no widening and no migration are needed. |
| Effective persisted bound | `min(20 whitespace-separated tokens, ≤200 chars ending on a word boundary)`. |
| Degenerate case | A single token longer than 200 chars falls back to a hard 200-char cut (still non-empty; blank-handling unaffected). |
| Unaffected | `SnackKeyword.definition` lives in the `snack.keywords` `JSON` column and has no such bound — **no char clamp** is applied in `SnackService.normalizeKeywords`. |

---

## 5. Migration / rollback

**Migration**: none required. No entity, column, index or table is added/altered/dropped. Previously
stored definitions (including 3-word ones) remain valid and unchanged.

**Rollback**: revert the source edits (two service constants, two prompts, four comments, three
backend tests, one frontend comment, one E2E spec) and redeploy. **No data change**, so rollback is
lossless and requires no SQL.

---

## 6. Key entities (spec cross-reference)

- **SnackKeyword** → §1.1: `definition` cap 3 → 20, stored in `Snack.keywords` JSON.
- **Keyword definition ("meaning")** → §2: the normalized policy (truncate over-limit, drop blank).
- **Meaning-snack source definition** → §1.2/§2/§4: same 20-token rule plus the ≤200-char word-boundary clamp (FR-019); `VARCHAR(200)` unchanged.
- **Definition card** (UI concept) → §1.3: verified readable at 20 words; escaped interpolation only.
- **Word-count rule** (concept) → §2: whitespace tokens, hyphenated = one, 20 inclusive.
