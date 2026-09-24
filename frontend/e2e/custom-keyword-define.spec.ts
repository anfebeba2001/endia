import { test, expect, type Page } from '@playwright/test';
import {
  setupAllMocks,
  setupDefinitionsMock,
  setupThemesHistoryMock,
  angularSubmitHome,
} from './helpers';
import type { ThemeHistory } from '../src/app/models/history.model';

/**
 * Feature 016 — custom keyword "Define" from a held text selection (2-second trigger).
 *
 * Covers, through route-mocked fixtures:
 *  - US1 (FR-001..FR-004, FR-008, SC-001/SC-002): the ~2s hold trigger, cancellation before 2s,
 *    release-without-activation (no request), Escape dismissal, and the success path (card opens for
 *    the term and shows the generated definition).
 *  - US2 (FR-006..FR-011, SC-002/SC-003/SC-005): loading state, rapid double-activation guard,
 *    failure with a meaningful error and nothing added, and the frontend bound feedback.
 *  - US3 (FR-013..FR-015, SC-004): the selected term and generated definition are escaped/inert.
 *  - US4 (FR-003/FR-016..FR-018, SC-006): programmatic selection, existing keyword clicks,
 *    single-card rule, and scope (results/history offer no trigger).
 */

interface FixtureSnack {
  id: string;
  subtopicId: string;
  subtopicName: string;
  concept: string;
  metaphor: string;
  whyItMatters: string;
  related: string[];
  keywords?: { term: string; definition: string }[];
}

/** A snack whose concept contains a plain (non-highlighted) word to define: "chemical". */
const SNACK: FixtureSnack = {
  id: 'snack-def-1',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept:
    'Photosynthesis converts light into chemical energy stored inside the strong bonds of glucose molecules.',
  metaphor: 'Think of a leaf as a solar kitchen.',
  whyItMatters: 'Nearly every food chain starts here.',
  related: ['Chlorophyll', 'Glucose'],
  keywords: [{ term: 'photosynthesis', definition: 'light to sugar' }],
};

/** 10 whitespace-separated tokens (the accepted upper bound, FR-010). */
const TEN_TOKENS = 'converts light into chemical energy stored inside the strong bonds';
/** 11 whitespace-separated tokens (rejected, FR-011). */
const ELEVEN_TOKENS = 'converts light into chemical energy stored inside the strong bonds of';

const DEFINE_BUTTON = '.selection-define__button';
const CARD = '.keyword-card';

// ─── Fixture helpers ─────────────────────────────────────────────────────────

async function overrideBatchWithSnack(page: Page, snack: FixtureSnack = SNACK): Promise<void> {
  await page.route('**/api/batches/*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        batchId: 'test-batch-001',
        themeId: 'test-theme-001',
        status: 'COMPLETE',
        retrying: false,
        items: [
          {
            subtopicId: snack.subtopicId,
            subtopicName: snack.subtopicName,
            snackStatus: 'READY',
            snack,
            snackCause: null,
            quizStatus: 'READY',
            quizCause: null,
          },
        ],
      }),
    }),
  );
}

async function goToSnackCard(page: Page): Promise<void> {
  await page.goto('/');
  await page.waitForTimeout(1500);
  await angularSubmitHome(page, 'Photosynthesis');
  await page.waitForURL('**/subtopics');
  await page.click('button.primary-btn');
  await page.waitForURL('**/snacks');
  await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });
}

/**
 * Create a real pointer selection over `searchText` inside `selector` using a mouse drag. Uses a
 * Range only to compute the target coordinates; the selection itself is made by the browser from
 * the drag (fires `selectionchange`), so this exercises the pointer path (FR-016).
 */
async function selectByMouse(page: Page, selector: string, searchText: string): Promise<void> {
  const box = await page.evaluate(
    ({ sel, txt }) => {
      const host = document.querySelector(sel);
      if (!host) {
        return null;
      }
      const walker = document.createTreeWalker(host, NodeFilter.SHOW_TEXT);
      let node: Node | null;
      while ((node = walker.nextNode())) {
        const content = node.textContent ?? '';
        const idx = content.indexOf(txt);
        if (idx >= 0) {
          const range = document.createRange();
          range.setStart(node, idx);
          range.setEnd(node, idx + txt.length);
          const rect = range.getBoundingClientRect();
          return { left: rect.left, right: rect.right, mid: rect.top + rect.height / 2 };
        }
      }
      return null;
    },
    { sel: selector, txt: searchText },
  );
  if (!box) {
    throw new Error(`text not found for pointer selection: ${searchText}`);
  }
  await page.mouse.move(box.left + 1, box.mid);
  await page.mouse.down();
  await page.mouse.move(box.right - 1, box.mid, { steps: 6 });
  await page.mouse.up();
}

/** Create a selection programmatically via `document.getSelection()` (FR-016). */
async function selectProgrammatically(
  page: Page,
  selector: string,
  searchText: string,
): Promise<void> {
  await page.evaluate(
    ({ sel, txt }) => {
      const host = document.querySelector(sel);
      if (!host) {
        throw new Error('host not found: ' + sel);
      }
      const walker = document.createTreeWalker(host, NodeFilter.SHOW_TEXT);
      let node: Node | null;
      while ((node = walker.nextNode())) {
        const content = node.textContent ?? '';
        const idx = content.indexOf(txt);
        if (idx >= 0) {
          const range = document.createRange();
          range.setStart(node, idx);
          range.setEnd(node, idx + txt.length);
          const selection = window.getSelection();
          selection?.removeAllRanges();
          selection?.addRange(range);
          document.dispatchEvent(new Event('selectionchange'));
          return;
        }
      }
      throw new Error('text not found for programmatic selection: ' + txt);
    },
    { sel: selector, txt: searchText },
  );
}

async function clearSelection(page: Page): Promise<void> {
  await page.evaluate(() => {
    window.getSelection()?.removeAllRanges();
    document.dispatchEvent(new Event('selectionchange'));
  });
}

// ─── US1: hold trigger, cancellation, success ────────────────────────────────

test.describe('US1 — held selection shows the Define affordance', () => {
  test('a held pointer selection shows the Define affordance', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await goToSnackCard(page);

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await expect(page.locator(DEFINE_BUTTON)).toHaveCount(0);

    await page.waitForTimeout(2200);
    await expect(page.locator(DEFINE_BUTTON)).toBeVisible();
    await expect(page.locator(DEFINE_BUTTON)).toHaveText(/Define/i);
  });

  test('no affordance appears before ~2 seconds', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await goToSnackCard(page);

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(500);
    await expect(page.locator(DEFINE_BUTTON)).toHaveCount(0);

    await page.waitForTimeout(2000);
    await expect(page.locator(DEFINE_BUTTON)).toBeVisible();
  });

  test('changing the selection before ~2s cancels and a fresh hold starts a new timer', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await goToSnackCard(page);

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(700);
    // Change the selection to a different word before the hold completes.
    await selectByMouse(page, '.snack-card__concept', 'light');
    await page.waitForTimeout(2200);
    // The affordance is for the new selection (still present after the fresh hold).
    await expect(page.locator(DEFINE_BUTTON)).toBeVisible();
  });

  test('clearing the selection before ~2s cancels the affordance', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await goToSnackCard(page);

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(700);
    await clearSelection(page);
    await page.waitForTimeout(2200);
    await expect(page.locator(DEFINE_BUTTON)).toHaveCount(0);
  });

  test('releasing without activating issues no definition request', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    let calls = 0;
    await setupDefinitionsMock(page, {
      generate: (body) => {
        calls += 1;
        return { status: 200, body: { term: body.term, definition: 'unused' } };
      },
    });
    await goToSnackCard(page);

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(2200);
    await expect(page.locator(DEFINE_BUTTON)).toBeVisible();

    // Clear the selection without activating Define.
    await page.locator('.page__title').click();
    await page.waitForTimeout(300);
    await expect(page.locator(DEFINE_BUTTON)).toHaveCount(0);
    expect(calls).toBe(0);
    await expect(page.locator(CARD)).toHaveCount(0);
  });

  test('Escape dismisses the affordance with no generation', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await goToSnackCard(page);

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(2200);
    await expect(page.locator(DEFINE_BUTTON)).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(page.locator(DEFINE_BUTTON)).toHaveCount(0);
    await expect(page).toHaveURL(/\/snacks$/);
  });

  test('activating Define opens the card for the term and shows the generated definition', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupDefinitionsMock(page, {
      generate: (body) => ({
        status: 200,
        delayMs: 700,
        body: { term: body.term, definition: 'the energy stored in the bonds of molecules' },
      }),
    });
    await goToSnackCard(page);

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(2200);
    await page.locator(DEFINE_BUTTON).click();

    const card = page.locator(CARD);
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__term')).toHaveText('chemical');

    await expect(card.locator('.keyword-card__definition')).toHaveText(
      'the energy stored in the bonds of molecules',
      { timeout: 5000 },
    );
  });
});

// ─── US2: loading, duplicate guard, safe failure, bounds feedback ────────────

test.describe('US2 — generation UX and safe failures', () => {
  test('a delayed generation shows a loading state then the definition', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupDefinitionsMock(page, {
      generate: (body) => ({
        status: 200,
        delayMs: 800,
        body: { term: body.term, definition: 'the energy stored in the bonds of molecules' },
      }),
    });
    await goToSnackCard(page);

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(2200);
    await page.locator(DEFINE_BUTTON).click();

    const card = page.locator(CARD);
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__loading')).toContainText(/Defining/i);

    await expect(card.locator('.keyword-card__definition')).toHaveText(
      'the energy stored in the bonds of molecules',
      { timeout: 5000 },
    );
    await expect(card.locator('.keyword-card__loading')).toHaveCount(0);
  });

  test('rapid double activation issues exactly one request and one card', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    let calls = 0;
    await setupDefinitionsMock(page, {
      generate: (body) => {
        calls += 1;
        return {
          status: 200,
          delayMs: 500,
          body: { term: body.term, definition: 'one definition only' },
        };
      },
    });
    await goToSnackCard(page);

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(2200);
    await page.locator(DEFINE_BUTTON).evaluate((el) => {
      (el as HTMLElement).click();
      (el as HTMLElement).click();
    });

    await expect(page.locator('.keyword-card__definition')).toHaveText('one definition only', {
      timeout: 5000,
    });
    expect(calls).toBe(1);
    await expect(page.locator(CARD)).toHaveCount(1);
  });

  test('a failed generation shows a meaningful error and adds no definition', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupDefinitionsMock(page, {
      generate: () => ({
        status: 503,
        body: {
          error: 'UNAVAILABLE',
          message: 'The AI service is temporarily unavailable. Please try again later.',
          cause: 'UNAVAILABLE',
        },
      }),
    });
    await goToSnackCard(page);

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(2200);
    await page.locator(DEFINE_BUTTON).click();

    const card = page.locator(CARD);
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__error')).toBeVisible();
    await expect(card.locator('.keyword-card__error')).toHaveAttribute('role', 'alert');
    await expect(card.locator('.keyword-card__error')).not.toHaveText('');
    // No empty/placeholder definition paragraph is rendered.
    await expect(card.locator('.keyword-card__definition')).toHaveCount(0);
    // The reading content is unaffected.
    await expect(page.locator('.snack-card__concept')).toContainText('chemical energy');
  });

  test('a whitespace-only selection shows an error and issues no request', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    let calls = 0;
    await setupDefinitionsMock(page, {
      generate: (body) => {
        calls += 1;
        return { status: 200, body: { term: body.term, definition: 'unused' } };
      },
    });
    await goToSnackCard(page);

    await selectProgrammatically(page, '.snack-card__concept', ' ');
    await page.waitForTimeout(2200);

    await expect(page.locator('.define-error')).toBeVisible();
    await expect(page.locator('.define-error')).toHaveAttribute('role', 'alert');
    expect(calls).toBe(0);
    await expect(page.locator(CARD)).toHaveCount(0);
  });

  test('an 11-token selection shows an error and issues no request', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    let calls = 0;
    await setupDefinitionsMock(page, {
      generate: (body) => {
        calls += 1;
        return { status: 200, body: { term: body.term, definition: 'unused' } };
      },
    });
    await goToSnackCard(page);

    await selectProgrammatically(page, '.snack-card__concept', ELEVEN_TOKENS);
    await page.waitForTimeout(2200);

    await expect(page.locator('.define-error')).toBeVisible();
    expect(calls).toBe(0);
    await expect(page.locator(CARD)).toHaveCount(0);
  });

  test('a 10-token selection is accepted and sent normalized', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    let sentTerm: string | undefined;
    await setupDefinitionsMock(page, {
      generate: (body) => {
        sentTerm = body.term;
        return { status: 200, body: { term: body.term, definition: 'ten words accepted' } };
      },
    });
    await goToSnackCard(page);

    await selectProgrammatically(page, '.snack-card__concept', TEN_TOKENS);
    await page.waitForTimeout(2200);
    await expect(page.locator(DEFINE_BUTTON)).toBeVisible();
    await page.locator(DEFINE_BUTTON).click();

    await expect(page.locator('.keyword-card__definition')).toHaveText('ten words accepted', {
      timeout: 5000,
    });
    expect(sentTerm).toBe(TEN_TOKENS);
    expect((sentTerm ?? '').split(/\s+/).length).toBe(10);
  });
});

// ─── US3: selected term and generated definition are escaped/inert ───────────

const XSS_DEFINITION =
  '<script>window.__xss=1</script> <img src=x onerror="window.__xss=1"> ' +
  'javascript:window.__xss=1 <iframe></iframe> <style>body{color:red}</style>';

const XSS_TERM_SNACK: FixtureSnack = {
  id: 'snack-def-xss',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: 'Safe concept with <script>window.__xss=1</script> payload text.',
  metaphor: 'Plain metaphor.',
  whyItMatters: 'Plain why.',
  related: ['Plain', 'Labels'],
  keywords: [],
};

async function assertNoInjection(page: Page, scope: string): Promise<void> {
  expect(
    await page.evaluate(() => (window as unknown as { __xss?: number }).__xss),
  ).toBeUndefined();
  for (const selector of ['script', 'img', 'iframe', 'style', 'a']) {
    await expect(page.locator(`${scope} ${selector}`)).toHaveCount(0);
  }
}

test.describe('US3 — untrusted term and definition are escaped', () => {
  test('a generated definition with payloads is inert and readable', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupDefinitionsMock(page, {
      generate: (body) => ({
        status: 200,
        body: { term: body.term, definition: XSS_DEFINITION },
      }),
    });
    await goToSnackCard(page);

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(2200);
    await page.locator(DEFINE_BUTTON).click();

    const card = page.locator(CARD);
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__definition')).toContainText('<script>');
    await assertNoInjection(page, CARD);
  });

  test('a selected term containing markup-like text is inert and readable', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, XSS_TERM_SNACK);
    await setupDefinitionsMock(page, {
      generate: (body) => ({
        status: 200,
        body: { term: body.term, definition: 'a benign definition' },
      }),
    });
    await goToSnackCard(page);

    await selectProgrammatically(page, '.snack-card__concept', '<script>window.__xss=1</script>');
    await page.waitForTimeout(2200);
    await page.locator(DEFINE_BUTTON).click();

    const card = page.locator(CARD);
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__term')).toContainText('<script>');
    await assertNoInjection(page, CARD);
  });
});

// ─── US4: reachability, non-interference and scope ───────────────────────────

async function overrideSummaryWithRecap(page: Page, snack: FixtureSnack): Promise<void> {
  await page.route('**/api/quiz/summary/*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        subtopicId: 'sub-001',
        totalQuestions: 4,
        correctAnswers: 3,
        totalXp: 30,
        questions: [],
        recap: { subtopicId: 'sub-001', subtopicName: 'Light reactions', snack },
      }),
    }),
  );
}

async function completeQuizToResults(page: Page): Promise<void> {
  await page.click('.quiz-btn');
  await page.waitForURL('**/quiz');
  await page.waitForTimeout(500);
  for (let i = 0; i < 4; i++) {
    await expect(page.locator('h2.question')).toBeVisible();
    await page.locator('.option').nth(0).click();
    await expect(page.locator('.feedback')).toBeVisible();
    await page.click('.feedback .primary-btn');
  }
  await page.waitForURL('**/results');
  await page.waitForTimeout(500);
}

const HISTORY_THEME: ThemeHistory[] = [
  {
    themeId: 'theme-1',
    prompt: 'Photosynthesis',
    createdAt: '2026-09-23T12:00:00',
    subtopics: [
      {
        id: 'sub-001',
        name: 'Light reactions',
        snacks: [
          {
            id: 'snack-1',
            concept: 'Photosynthesis converts light into chemical energy.',
            metaphor: 'Think of a leaf as a solar kitchen.',
            whyItMatters: 'Nearly every food chain starts here.',
            related: ['Chlorophyll', 'Glucose'],
            keywords: [{ term: 'photosynthesis', definition: 'light to sugar' }],
          },
        ],
        quizQuestions: [],
      },
    ],
  },
];

test.describe('US4 — reachability, non-interference and scope', () => {
  test('a programmatic selection held ~2s shows the Define affordance', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await goToSnackCard(page);

    await selectProgrammatically(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(2200);
    await expect(page.locator(DEFINE_BUTTON)).toBeVisible();
  });

  test('clicking an existing highlighted keyword opens the card without the selection flow', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await goToSnackCard(page);

    await page
      .locator('.snack-card__concept .rich-text__keyword[data-term="photosynthesis"]')
      .first()
      .click();

    const card = page.locator(CARD);
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__term')).toHaveText('photosynthesis');
    await expect(card.locator('.keyword-card__definition')).toHaveText('light to sugar');
    await expect(page.locator(DEFINE_BUTTON)).toHaveCount(0);
    await expect(page.locator('.keyword-card__loading')).toHaveCount(0);
  });

  test('the single-card rule holds when a custom definition replaces a keyword card', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupDefinitionsMock(page, {
      generate: (body) => ({
        status: 200,
        body: { term: body.term, definition: 'the custom definition' },
      }),
    });
    await goToSnackCard(page);

    await page
      .locator('.snack-card__concept .rich-text__keyword[data-term="photosynthesis"]')
      .first()
      .click();
    await expect(page.locator(CARD)).toHaveCount(1);
    await expect(page.locator('.keyword-card__term')).toHaveText('photosynthesis');

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(2200);
    await page.locator(DEFINE_BUTTON).click();

    await expect(page.locator(CARD)).toHaveCount(1);
    await expect(page.locator('.keyword-card__term')).toHaveText('chemical');
    await expect(page.locator('.keyword-card__definition')).toHaveText('the custom definition', {
      timeout: 5000,
    });
  });

  test('the results recap offers no trigger and renders unchanged', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await overrideSummaryWithRecap(page, SNACK);
    await goToSnackCard(page);
    await completeQuizToResults(page);

    const recap = page.locator('.recap-card__concept');
    await expect(recap).toBeVisible();
    await selectProgrammatically(page, '.recap-card__concept', 'chemical');
    await page.waitForTimeout(2200);
    await expect(page.locator(DEFINE_BUTTON)).toHaveCount(0);
    await expect(page.locator('.define-error')).toHaveCount(0);
    await expect(recap).toContainText('chemical energy');
  });

  test('the read-only history surface offers no trigger and renders unchanged', async ({ page }) => {
    await setupAllMocks(page);
    await setupThemesHistoryMock(page, HISTORY_THEME);
    await page.goto('/history');
    await page.waitForTimeout(1500);

    await page.locator('.history-card__toggle').first().click();
    const concept = page.locator('.history-snack__concept');
    await expect(concept).toBeVisible();
    await selectProgrammatically(page, '.history-snack__concept', 'chemical');
    await page.waitForTimeout(2200);
    await expect(page.locator(DEFINE_BUTTON)).toHaveCount(0);
    await expect(page.locator('.define-error')).toHaveCount(0);
    await expect(concept).toContainText('chemical energy');
  });
});
