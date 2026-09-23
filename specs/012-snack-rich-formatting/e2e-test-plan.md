# Snack Rich Formatting E2E Test Plan

## Application Overview

Feature 012 changes how snack text is *written* and *displayed*: the backend prompt now emits
`**bold**` emphasis, `- ` bullet lines and blank-line paragraph breaks inside the unchanged snack
JSON (`concept`, `metaphor`, `whyItMatters`, `related`), and a shared frontend renderer
(`frontend/src/app/components/rich-text/`) converts that whitelist into safe DOM (`<strong>`,
`<ul><li>`, `<p>`, `<br>`) on the snack card, the results recap and the read-only history view.
Anything outside the whitelist (`<script>`, `onerror`, `javascript:`, `<iframe>`, `<style>`, links)
must be neutralized as inert escaped text, never executed or injected.

The frontend is an Angular 21 SPA served at `http://localhost:4200`; the backend may not be runnable
(no Docker), so every scenario uses the repo's established Playwright `page.route(...)` mock pattern
(`frontend/e2e/helpers.ts`). Tests are deterministic and independent; each installs its own mocks and
navigates from `/` (or deep-links to `/history`).

Backend prompt/round-trip behavior (FR-001..FR-009) is covered by the backend JUnit suite and is out
of scope for E2E; this plan covers the rendered UI and its security boundary.

## Test Scenarios

### 1. Snack card rich rendering

**Seed:** `frontend/e2e/snack-rich-formatting.spec.ts` (base mocks + markup-bearing batch snack)

#### 1.1. bold-becomes-strong
**File:** `frontend/e2e/snack-rich-formatting.spec.ts`
**Steps:**
  1. Land on `/snacks` with a snack whose `concept` contains `**Photosynthesis**` and bullet bold terms.
   - expect: `.snack-card__concept strong` texts are `Photosynthesis`, `Light reactions`, `Calvin cycle`.
   - expect: `.snack-card` contains no literal `**`.

#### 1.2. bullet-lines-become-list-items
**File:** `frontend/e2e/snack-rich-formatting.spec.ts`
**Steps:**
  1. Render the same snack card.
   - expect: `.snack-card__concept ul` count 1; `li` count 2; first `li` shows no leading `- `.

#### 1.3. bullet-variants-star-and-bullet
**File:** `frontend/e2e/snack-rich-formatting.spec.ts`
**Steps:**
  1. Render a snack whose `concept` uses `* ` and `• ` bullet markers.
   - expect: a single `ul` with the correct number of `li`; no literal `* `/`• ` marker text.

#### 1.4. blank-lines-become-paragraphs
**File:** `frontend/e2e/snack-rich-formatting.spec.ts`
**Steps:**
  1. Render the markup snack.
   - expect: `.snack-card__concept p` count 2; the second paragraph carries the intro sentence (and is
     not merged with the list).

#### 1.5. single-newline-becomes-break
**File:** `frontend/e2e/snack-rich-formatting.spec.ts`
**Steps:**
  1. Render a snack whose `concept` contains a single `\n` inside one paragraph.
   - expect: one `p` containing a `<br>`; both sides of the break are readable.

#### 1.6. related-stays-plain-labels
**File:** `frontend/e2e/snack-rich-formatting.spec.ts`
**Steps:**
  1. Render the markup snack.
   - expect: `.snack-card__related .tag` count 2, first text `Chlorophyll`; no `strong` inside related.

### 2. Results recap rich rendering

**Seed:** `frontend/e2e/snack-rich-formatting.spec.ts` (base mocks + recap summary override)

#### 2.1. recap-renders-rich-formatting
**File:** `frontend/e2e/snack-rich-formatting.spec.ts`
**Steps:**
  1. Complete the quiz to `/results` with a recap snack carrying bold/bullets/paragraphs.
   - expect: `.recap-card__concept strong` first text `Photosynthesis`; `ul li` count 2; `p` count 2;
     no literal `**` in `.recap-card`.

### 3. History view rich rendering (feature 011 surface)

**Seed:** `frontend/e2e/theme-history.spec.ts` (`GET /api/themes` mock)

#### 3.1. history-renders-rich-formatting
**File:** `frontend/e2e/theme-history.spec.ts`
**Steps:**
  1. Deep-link `/history` and expand the first theme.
   - expect: `.history-snack__concept strong` texts; `ul li` count 2; `p` count 2; no literal `**`;
     `related` list stays plain (no `strong`).

### 4. Security — untrusted markup neutralized

**Seed:** `frontend/e2e/snack-rich-formatting.spec.ts` / `frontend/e2e/theme-history.spec.ts` (payload fixtures)

#### 4.1. snack-card-neutralizes-payloads
**File:** `frontend/e2e/snack-rich-formatting.spec.ts`
**Steps:**
  1. Render a snack with `<script>`, `<img onerror>`, `javascript:` link, `<iframe>`, `<style>`.
   - expect: `window.__xss` undefined; zero `script`/`img`/`iframe`/`style`/`a` inside `.snack-card`;
     readable text preserved.

#### 4.2. recap-neutralizes-payloads
**File:** `frontend/e2e/snack-rich-formatting.spec.ts`
**Steps:**
  1. Render the same payloads through the recap.
   - expect: `window.__xss` undefined; zero injected nodes; readable text preserved.

#### 4.3. history-neutralizes-payloads
**File:** `frontend/e2e/theme-history.spec.ts`
**Steps:**
  1. Render payload snacks in the expanded history detail.
   - expect: `window.__xss` undefined; zero injected nodes; readable text preserved.

### 5. No-markup / legacy text

**Seed:** `frontend/e2e/snack-rich-formatting.spec.ts` (plain fixtures)

#### 5.1. plain-text-not-mangled
**File:** `frontend/e2e/snack-rich-formatting.spec.ts`
**Steps:**
  1. Render a snack with no markup, a stray `5 * 3 = 15`, and an unclosed `**important`.
   - expect: exactly one `p`, zero `ul`, zero `strong`; `5 * 3 = 15` readable; unclosed `**` stays
     literal and does not swallow the rest of the field.

### 6. Non-regression

**Seed:** full existing suite

#### 6.1. full-suite-unchanged
**File:** `frontend/e2e/*.spec.ts`
**Steps:**
  1. Run the entire Playwright suite.
   - expect: all pre-existing suites (happy path, progressive delivery, results, navigation, history)
     pass unchanged alongside the new feature specs.
