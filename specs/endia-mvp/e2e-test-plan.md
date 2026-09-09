# Endia MVP E2E Test Plan

## Application Overview

Endia is an Angular 21 learning application with a Spring Boot 3.4 backend. The E2E flow covers:
**Home** (enter theme) → **Subtopics** (pick focus areas) → **Snacks** (memorable cards) → **Quiz** (answer questions) → **Results** (score breakdown) → **Restart**.

All API calls are intercepted via Playwright's `page.route()` to avoid LLM dependency. Angular's reactive form `(ngSubmit)` does not fire on Playwright's native click, so the Home page submit uses Angular's debug API (`ng.getComponent()`).

## Test Architecture

- **Framework**: Playwright Test with Chromium
- **Mocking**: All 5 API endpoints intercepted (no real LLM needed)
- **Angular Integration**: `ng.getComponent()` used for Home form submit (workaround for ngSubmit + Playwright incompatibility)
- **Config**: `frontend/playwright.config.ts` with `webServer` auto-starting `ng serve`
- **Tests**: 17 tests across 10 files in `frontend/e2e/`

## Test Scenarios

### P0 — Must Pass (Core Loop)

| # | Scenario | File | Status |
|---|----------|------|--------|
| 1 | Full happy path | `e2e/happy-path.spec.ts` | ✅ PASS |
| 2 | Input validation (short + empty) | `e2e/input-validation.spec.ts` | ✅ PASS |
| 3 | Subtopic deselection | `e2e/subtopic-deselection.spec.ts` | ✅ PASS |
| 4 | Quiz correct answer feedback | `e2e/quiz-feedback.spec.ts` | ✅ PASS |
| 5 | Quiz wrong answer feedback | `e2e/quiz-feedback.spec.ts` | ✅ PASS |
| 6 | Results accuracy | `e2e/results-accuracy.spec.ts` | ✅ PASS |
| 7 | Restart clears state | `e2e/restart-clears-state.spec.ts` | ✅ PASS |

### P1 — Important

| # | Scenario | File | Status |
|---|----------|------|--------|
| 8 | Back navigation (subtopics→home, snacks→subtopics) | `e2e/back-navigation.spec.ts` | ✅ PASS |
| 9 | Loading states (home + snacks) | `e2e/loading-states.spec.ts` | ✅ PASS |
| 10 | Error recovery (500 → retry) | `e2e/error-recovery.spec.ts` | ✅ PASS |
| 11 | Direct URL access (quiz, results, subtopics) | `e2e/direct-url-access.spec.ts` | ✅ PASS |

### P2 — Nice to Have

| # | Scenario | File | Status |
|---|----------|------|--------|
| 12 | Perfect score (4/4 correct → trophy) | `e2e/perfect-score.spec.ts` | ✅ PASS |

## Notable Findings

### Angular (ngSubmit) + Playwright Incompatibility

Playwright's native `click()` on a `<button type="submit">` does NOT trigger Angular's `(ngSubmit)` directive. The native form GET submission fires instead, bypassing Angular's event handler entirely. This was confirmed via:

- `page.click('button[type=submit]')` → no API call, no state change
- `form.requestSubmit()` → no API call
- `dispatchEvent(new Event('submit'))` → no API call
- `ng.getComponent(el).onSubmit()` → **works perfectly**

**Root cause**: Angular's `(ngSubmit)` listens for the form's `submit` event, but Playwright's click dispatches the event through a path that zone.js doesn't patch, or the native form submission occurs before Angular can intercept it.

**Workaround**: Use `ng.getComponent()` to access the Angular component directly and call its methods. This is documented in `e2e/helpers.ts` as `angularSubmitHome()`.

### Route Guard Behavior

- `/quiz` → redirects to `/snacks` (has guard: requires `selectedSubtopic` in state)
- `/results` → redirects to `/snacks` (has guard: requires `quizSubTopicId` in state)
- `/subtopics` → does NOT redirect (shows empty state with no subtopic cards)
