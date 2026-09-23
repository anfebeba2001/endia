# Implementation Plan: Snack Rich Formatting (Bold Emphasis, Bullets, Short Paragraphs)

**Branch**: `012-snack-rich-formatting` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-snack-rich-formatting/spec.md`

## Summary

End-to-end formatting feature across three layers, with **no schema and no JSON-contract change**:

1. **Prompt (backend, FR-001..FR-006)**: rewrite
   `backend/src/main/resources/prompts/generate-snack.md` so the model (a) wraps important
   words/sentences in `**...**`, (b) splits long content into short paragraphs (blank-line separated)
   and `- ` bullet lines, and (c) is constrained to that whitelist while forbidding HTML/links/images/
   scripts/headings/tables/code/inline styles. The JSON shape `{concept, metaphor, whyItMatters,
   related}` is unchanged and `related` stays plain names. This is the **only** backend change.
2. **Persistence/API (FR-007..FR-009, US4)**: verify (do not modify) that markup survives the round
   trip. `Snack.concept/metaphor/whyItMatters` are MySQL `TEXT`; `OpenAiLlmProvider` reads fields via
   Jackson `asText`; `SnackService` validates but never trims/escapes; the DTOs expose the raw strings.
   No production code change; covered by tests.
3. **Rendering (frontend, FR-010..FR-019, FR-023/FR-024, US2/US3/US5)**: introduce **one shared,
   dependency-free rich-text renderer** (`RichTextComponent` + a pure `parseRichText` parser) that
   converts the whitelisted markup into a typed block AST and renders it with plain Angular template
   bindings — **no `[innerHTML]`, no `DomSanitizer`, no new dependency**. The component is reused on
   **all three** snack-displaying surfaces: the snack card, the end-of-test results recap, and the
   read-only history view (feature 011).

The renderer is the single sanitization boundary (FR-017/FR-018): because output is built from typed
nodes rendered through Angular's default text interpolation, every character is escaped by Angular,
and only the three whitelisted constructs can ever become elements.

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.4.x (backend); TypeScript 5.9.x + Angular 21.2.x
(frontend, standalone components, signals-first, `ChangeDetectionStrategy.OnPush`).

**Primary Dependencies**: Spring Web / Spring Data JPA / Hibernate / Jackson (backend); Angular core,
Router, signals, RxJS, `@angular/common/http` (frontend). **No new runtime dependency in either
project** (FR constraint / research.md R1).

**Storage**: MySQL 8.x. **No schema or migration change.** `Snack.concept/metaphor/whyItMatters` are
already `TEXT`; `related` is a JSON list via `JpaJsonConverter`. `ddl-auto=update` produces no delta.

**Testing**:
- Backend: JUnit 5 + Mockito unit tests; MockMvc contract tests; Testcontainers MySQL integration
  tests (tagged `integration`, run via `mvn verify -Pintegration`, excluded from default surefire).
- Frontend: Playwright E2E only (there is **no** Karma/Jest runner in `frontend/`), plus
  `npm run build` for type-checking. New parser behavior is covered through route-mocked E2E fixtures.

**Target Platform**: Modern desktop browsers (Chrome/Firefox/Safari last 2 versions); Linux server.

**Project Type**: Web-application monorepo (`backend/` + `frontend/`).

**Performance Goals**: No measurable change. Rendering a snack field is a linear single-pass parse
over a short string (a few hundred characters), executed once per field under `computed()`. No
network, no storage, no generation-latency change.

**Constraints**:
- API/JSON contract and DB schema **unchanged** (FR-009, SC-007, Non-Goals).
- Backend preserves model text **unmodified**; sanitization happens at the frontend render boundary
  (FR-017, Clarification 3).
- Existing snack/quiz/results/progressive-delivery E2E suites must pass unchanged (FR-021, SC-008).

**Scale/Scope**: 3 UI surfaces (snack card, results recap, history view) × 3 text fields
(`concept`, `metaphor`, `whyItMatters`). `related` is explicitly **not** rich text. 1 prompt file, 1
new shared component + parser, 3 call-site updates, tests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment | Verdict |
|-----------|------------|---------|
| **I. Spec-Driven Development** | `spec.md` exists with 24 FRs and is the source of truth; this `plan.md` and downstream `tasks.md` follow. | PASS |
| **II. Clean Architecture (Layered)** | Backend change is confined to a `resources/prompts/*.md` asset — no layer crossing, no domain/application/infrastructure/interfaces code touched. Frontend adds a reusable standalone component under `components/`, signals-first, OnPush, no service/provider coupling. | PASS |
| **III. LLM Abstraction First** | The prompt stays externalized (`generate-snack.md` loaded by `LlmPromptTemplates`); `LlmProvider` and provider implementations are untouched. | PASS |
| **IV. Quality Gate for Generated Content** | The existing snack validation (non-empty fields, `related` 2–3 entries, single re-roll) is preserved unchanged; formatting markup does not affect it (FR-006). Quiz quality gate untouched. | PASS |
| **V. MVP-First, Iterate / YAGNI** | Smallest viable change: prompt wording + one shared renderer. No Markdown library, no full dialect, no schema change, no backend rendering. | PASS |

**Re-check after Phase 1 design**: No new violations. No violations to justify → **Complexity
Tracking table is intentionally empty.**

## Project Structure

### Documentation (this feature)

```text
specs/012-snack-rich-formatting/
├── plan.md              # This file
├── research.md          # Phase 0 output (renderer + sanitization + round-trip decisions)
├── data-model.md        # Phase 1 output (confirms NO schema change)
├── quickstart.md        # Phase 1 output (run + manual verification)
├── contracts/           # Phase 1 output
│   ├── snack-text-format.md      # markup grammar + surface contract
│   └── snack-response.sample.json# unchanged JSON shape with markup-bearing fields
├── checklists/          # (existing, if any)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root) — files touched by this feature

```text
backend/
└── src/
    ├── main/resources/prompts/
    │   └── generate-snack.md            # [EDIT] formatting instructions (only prod change)
    └── test/java/com/endia/
        ├── infrastructure/llm/
        │   └── ...GenerateSnackPromptTest.java        # [NEW] prompt contains whitelist + shape rules
        └── interfaces/rest/
            └── ...SnackMarkupRoundTripTest.java        # [NEW] markup survives API unchanged

frontend/
├── src/app/
│   ├── components/
│   │   └── rich-text/                              # [NEW] shared sanitized renderer
│   │       ├── rich-text.component.ts              # standalone, signal input, OnPush
│   │       ├── rich-text.component.html            # @for blocks → <p>/<ul><li>/<strong>, no innerHTML
│   │       ├── rich-text.component.scss            # typography + vertical rhythm, token-based
│   │       ├── rich-text.parser.ts                 # pure parseRichText(text): RichBlock[]
│   │       └── rich-text.model.ts                  # RichBlock / RichInline types
│   ├── pages/
│   │   ├── snacks/snacks.component.{ts,html}       # [EDIT] concept/metaphor/whyItMatters
│   │   ├── results/results.component.{ts,html}     # [EDIT] recap concept/metaphor
│   │   └── history/history.component.{ts,html}     # [EDIT] nested snack concept/metaphor/whyItMatters
│   └── models/                                     # [UNCHANGED] snack/batch/history/quiz models
└── e2e/
    ├── snack-rich-formatting.spec.ts               # [NEW] bold/bullets/paragraphs + XSS neutralization
    └── theme-history.spec.ts                       # [EDIT/EXTEND] rich rendering in history
```

**Structure Decision**: Web-application monorepo. Backend follows the existing classic layered
architecture (`domain → application → infrastructure → interfaces`); this feature touches only the
externalized prompt asset, so no layer is added or modified. Frontend follows the Angular 21
standalone/signals convention: the shared renderer lives under `frontend/src/app/components/`
(alongside the existing `drag-drop-question/` component) and is imported by the three standalone page
components. The parser is a pure function separate from the component so it is unit-testable and
side-effect-free.

### Exact call sites that must adopt the shared renderer

| Surface | Component | File(s) | Current markup |
|---------|-----------|---------|----------------|
| **Snack card** | `SnacksComponent` | `frontend/src/app/pages/snacks/snacks.component.html` (lines 82, 85, 88) + `.ts` imports | `<p class="snack-card__concept">{{ snack.concept }}</p>` etc. |
| **Results recap** | `ResultsComponent` | `frontend/src/app/pages/results/results.component.html` (lines 84, 85) + `.ts` imports | `<p class="recap-card__concept">{{ snack.concept }}</p>` etc. |
| **History view** | `HistoryComponent` | `frontend/src/app/pages/history/history.component.html` (lines 62, 63, 64) + `.ts` imports | `<p class="history-snack__concept">{{ snack.concept }}</p>` etc. |

`related` rendering (tags / `history-snack__related`) is **unchanged** and stays plain text.

## Design: Shared Rich-Text Renderer

### Contract of the component

```ts
@Component({
  selector: 'app-rich-text',
  standalone: true,
  templateUrl: './rich-text.component.html',
  styleUrl: './rich-text.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RichTextComponent {
  readonly text = input<string>('');            // raw stored field value
  protected readonly blocks = computed(() => parseRichText(this.text()));
}
```

Call sites: `<app-rich-text [text]="snack.concept" class="snack-card__concept" />`. The existing
typography class moves to the host so font/color tokens still apply; the component's inner
`<p>`/`<ul>` are margin-normalized and provide paragraph/list rhythm.

### Parser (`parseRichText`) — the whitelist

Pure, synchronous, dependency-free, single pass:

1. Normalize line endings (`\r\n`/`\r` → `\n`).
2. Split into lines and group into blocks:
   - **blank line** → paragraph boundary;
   - **line matching** `/^\s*[-*•]\s+/` → bullet item (consecutive items form one list block; the
     marker itself is discarded);
   - **anything else** → paragraph line (consecutive lines form one paragraph; a single `\n` inside a
     paragraph becomes a soft break so authored line breaks are preserved, not collapsed).
3. Inline pass over each paragraph/list item: tokenize `**` toggles into `text` / `bold` spans.
   - A bold span is only recognized when the opening `**` is immediately followed by a non-space and
     the closing `**` is immediately preceded by a non-space (Markdown-like rule). This avoids
     mangling `5 * 3 = 15` or `**  ` sequences.
   - An **unclosed** `**` is emitted as literal text (never swallows the rest of the field).
   - `\n` inside a paragraph/list item becomes an explicit `break` inline node.
4. A minimal HTML-entity decode (`&amp; &lt; &gt; &quot; &#39;` and numeric references) is applied to
   text nodes only, so a stored `&lt;` displays as `<` without double-escaping (spec edge case).
   Decoding to plain text is safe because output is rendered as text nodes, never as markup.
5. Empty/whitespace-only input yields **zero blocks** → no empty `<p>`/`<ul>` artifact (FR-014).

### Rendering (`rich-text.component.html`)

```html
@for (block of blocks(); track $index) {
  @if (block.kind === 'list') {
    <ul class="rich-text__list">
      @for (item of block.items; track $index) {
        <li class="rich-text__item"><ng-container *ngTemplateOutlet="inline; context: { nodes: item }" /></li>
      }
    </ul>
  } @else {
    <p class="rich-text__p"><ng-container *ngTemplateOutlet="inline; context: { nodes: block.nodes }" /></p>
  }
}
<ng-template #inline let-nodes="nodes">
  @for (node of nodes; track $index) {
    @switch (node.kind) {
      @case ('bold') { <strong class="rich-text__strong">{{ node.text }}</strong> }
      @case ('break') { <br /> }
      @default { {{ node.text }} }
    }
  }
</ng-template>
```

No `[innerHTML]` anywhere. Angular escapes every interpolated text node, so raw HTML/script/event
handlers in stored text are displayed as inert text (FR-016, FR-019) and can never become elements.

### Sanitization approach (summary — full rationale in research.md R1)

- **Chosen**: a small allowlist **parser-to-AST** rendered with Angular template bindings. The
  whitelist is exactly three constructs; the output contains only `<p>`, `<ul>`, `<li>`, `<strong>`,
  `<br>`. No HTML string is ever constructed or trusted, so there is no bypass path.
- **Rejected as primary**: `DomSanitizer` + `[innerHTML]`. It operates on HTML strings, its default
  allowlist is broader than ours (it permits links/images and many tags we must neutralize), and it
  requires either `sanitize()` (which re-parses HTML we would have had to build) or
  `bypassSecurityTrustHtml` (which disables the guard). It adds complexity without a tighter
  guarantee than the AST approach, and the feature needs **no** new dependency.
- `DomSanitizer` is **not needed** by the final design. It remains documented as the fallback only if
  a future requirement demands true Markdown/HTML parsing.

## Milestones / Phases

| # | Milestone | Scope | FRs | Depends on |
|---|-----------|-------|-----|------------|
| **M1** | Prompt emits formatting | Rewrite `generate-snack.md`; keep JSON shape; whitelist + forbid HTML | FR-001..FR-006 | — |
| **M2** | Round-trip verification | Tests proving `**`/bullets/paragraphs survive JPA `TEXT` + API on all surfaces; no prod code change | FR-007..FR-009 | M1 (fixtures) |
| **M3** | Shared renderer | `rich-text.parser.ts`, `rich-text.model.ts`, `RichTextComponent` (+ styles) | FR-010..FR-012, FR-014..FR-019 | — (parallel with M1) |
| **M4** | Surface integration | Adopt renderer in snacks card, results recap, and history; `related` stays plain | FR-013, FR-020, FR-023, FR-024 | M3 |
| **M5** | Security + regression | XSS/injection E2E, no-markup/old-snack fixtures, full existing E2E suite | FR-021, FR-022, SC-005..SC-009 | M4 |

Rough effort ordering: M1 and M3 are independent and can proceed in parallel; M2 validates M1; M4
needs M3; M5 closes the loop. Total surface area is small (1 prompt file, 1 component + parser, 3
call-site edits, tests).

## Testing Strategy

**Backend**
- *Prompt unit test*: assert the rendered `generate-snack.md` instructs bold `**`, `- ` bullets,
  short paragraphs, the whitelist, forbids HTML, and still specifies exactly
  `concept/metaphor/whyItMatters/related` (FR-001..FR-004).
- *Validation test*: existing `SnackService` validation still passes for formatted content and does
  not cause spurious re-rolls (FR-006).
- *Round-trip contract/integration test*: persist a snack whose fields contain `**bold**`, bullet
  lines, blank-line paragraphs, and `<script>`/`onerror` payloads; read it back through the fresh
  generation response, batch status, results recap, and `GET /api/themes` history; assert the decoded
  string equals the generated value (newlines/bullets intact, not HTML-escaped), and the JSON shape is
  unchanged (FR-007..FR-009, SC-006/SC-007).

**Frontend (Playwright E2E — the only runner in the repo)**
- *`snack-rich-formatting.spec.ts`*: route-mock the API to return fixed snacks containing
  `**bold**`, `- ` / `*` / `•` bullets, multiple blank-line paragraphs, an unclosed `**`, a stray `*`,
  and plain text. Assert on the snack card: `<strong>` present, `<ul><li>` for bullets, multiple `<p>`,
  **zero** literal `**` or leading-dash text, plain fields render cleanly with no empty `<p>`/`<ul>`
  (US1/US2/US5).
- *Same spec, results recap*: navigate to `/results` with a mocked summary containing a recap snack and
  assert the same rendering (FR-013).
- *History extension in `theme-history.spec.ts`*: mocked `GET /api/themes` with nested marked-up snacks;
  assert rich rendering in the expanded history detail and no literal markers (FR-023, SC-009).
- *Security spec*: fields containing `<script>window.__xss=1</script>`, `<img src=x onerror=...>`,
  `javascript:` links, `<iframe>`, inline styles. Assert `window.__xss` is undefined, no injected
  `<script>`/`<img>`/`<iframe>` nodes exist in the DOM, and readable text is preserved (FR-015..FR-019,
  SC-005). Covers snack card, recap, and history (FR-024).
- *Regression*: run the existing suites (`happy-path`, `progressive-delivery`, `snack-wait-messages`,
  `results-*`, `snacks-navigation`, `theme-history`, etc.) unchanged (FR-021, SC-008).

No new test runner or dependency is introduced.

## Complexity Tracking

> No Constitution Check violations. This section is intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
