import { test, expect, type Page } from '@playwright/test';
import {
  setupAllMocks,
  setupDefinitionsMock,
  setupMeaningSnacksMock,
  meaningSnack,
  angularSubmitHome,
  type MeaningSnackFixture,
} from './helpers';

/**
 * Feature 017 — "Create snack" from a custom keyword's generated meaning (Task 12).
 *
 * Completes the feature-016 custom-keyword journey: once the held-selection "Define" flow has
 * generated and displayed a meaning, the definition card offers exactly one **Create snack** action
 * that turns that term + generated meaning into a feature-014 **meaning snack** in the backbone —
 * quiz-free, visually distinct, persistent — reusing the existing generation/persistence path.
 *
 * Covers, through route-mocked fixtures:
 *  - US1 (FR-001..FR-010, SC-001..SC-003): availability only after a valid meaning, exactly one
 *    relabeled action, the captured source subtopic (regular `subtopicId` / meaning
 *    `sourceSubtopicId`), loading, success (one entry), failure (no entry), rapid-double guard,
 *    re-open `done`, and the server-side idempotent branch.
 *  - US2 (FR-011..FR-013, SC-004): the created entry is distinct and offers no quiz; the regular
 *    snack keeps its quiz affordance.
 *  - US3 (FR-014/FR-015, SC-005): persistence across a reload/new session and graceful degradation.
 *  - US4 (FR-017..FR-019, SC-006): the custom term, generated meaning, and created content are
 *    escaped/inert.
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

/** A regular snack whose concept contains a plain (non-highlighted) word to define: "chemical". */
const SNACK: FixtureSnack = {
  id: 'snack-create-1',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept:
    'Photosynthesis converts light into chemical energy stored inside the strong bonds of glucose molecules.',
  metaphor: 'Think of a leaf as a solar kitchen.',
  whyItMatters: 'Nearly every food chain starts here.',
  related: ['Chlorophyll', 'Glucose'],
  keywords: [{ term: 'photosynthesis', definition: 'light to sugar' }],
};

const GENERATED_MEANING = 'the energy stored in the bonds of molecules';

const DEFINE_BUTTON = '.selection-define__button';
const CARD = '.keyword-card';
const GENERATE_BUTTON = '.keyword-card__generate';
const meaningIndexItems = (page: Page) => page.locator('.snack-index__item--meaning');

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
 * Create a real pointer selection over `searchText` inside `selector` using a mouse drag (the
 * feature-016 trigger path). Uses a Range only to compute coordinates.
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

/** Hold a selection ~2.2s and activate Define, leaving the custom card open. */
async function defineTerm(page: Page, selector: string, term: string): Promise<void> {
  await selectByMouse(page, selector, term);
  await page.waitForTimeout(2200);
  await page.locator(DEFINE_BUTTON).click();
}

// ─── US1: action availability ────────────────────────────────────────────────

test.describe('US1 — Create snack availability', () => {
  test('the action appears only after Define completes, then shows Create snack', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupDefinitionsMock(page, {
      generate: (body) => ({
        status: 200,
        delayMs: 800,
        body: { term: body.term, definition: GENERATED_MEANING },
      }),
    });
    await goToSnackCard(page);

    await selectByMouse(page, '.snack-card__concept', 'chemical');
    await page.waitForTimeout(2200);
    await page.locator(DEFINE_BUTTON).click();

    const card = page.locator(CARD);
    await expect(card).toBeVisible();
    // While defining, no snack-creation action is offered.
    await expect(card.locator('.keyword-card__loading')).toBeVisible();
    await expect(card.locator(GENERATE_BUTTON)).toHaveCount(0);

    // After the meaning resolves, exactly one action labelled "Create snack".
    await expect(card.locator('.keyword-card__definition')).toHaveText(GENERATED_MEANING, {
      timeout: 5000,
    });
    await expect(card.locator(GENERATE_BUTTON)).toHaveCount(1);
    await expect(card.locator(GENERATE_BUTTON)).toHaveText(/Create snack/i);
  });

  test('no action is offered when the definition generation fails', async ({ page }) => {
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

    await defineTerm(page, '.snack-card__concept', 'chemical');

    const card = page.locator(CARD);
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__error')).toBeVisible();
    await expect(card.locator('.keyword-card__error')).toHaveAttribute('role', 'alert');
    await expect(card.locator(GENERATE_BUTTON)).toHaveCount(0);
    await expect(meaningIndexItems(page)).toHaveCount(0);
  });

  test('no action is offered when the generated meaning is blank', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    // A successful response with an empty definition is not a valid generated meaning (FR-002).
    await setupDefinitionsMock(page, {
      generate: (body) => ({ status: 200, body: { term: body.term, definition: '' } }),
    });
    await goToSnackCard(page);

    await defineTerm(page, '.snack-card__concept', 'chemical');

    const card = page.locator(CARD);
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__term')).toHaveText('chemical');
    await expect(card.locator('.keyword-card__loading')).toHaveCount(0);
    await expect(card.locator('.keyword-card__error')).toHaveCount(0);
    await expect(card.locator(GENERATE_BUTTON)).toHaveCount(0);
    await expect(meaningIndexItems(page)).toHaveCount(0);
  });

  test('a regular highlighted-keyword card still shows Generate snack', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await goToSnackCard(page);

    await page
      .locator('.snack-card__concept .rich-text__keyword[data-term="photosynthesis"]')
      .first()
      .click();

    const button = page.locator(GENERATE_BUTTON);
    await expect(button).toBeVisible();
    await expect(button).toHaveText(/Generate snack/i);
    await expect(button).not.toHaveText(/Create snack/i);
  });
});

// ─── US1: creation & backbone integration ────────────────────────────────────

test.describe('US1 — creation and backbone integration', () => {
  test('creating posts the captured regular subtopic and adds exactly one meaning snack', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupDefinitionsMock(page, {
      generate: (body) => ({
        status: 200,
        body: { term: body.term, definition: GENERATED_MEANING },
      }),
    });
    let sent: { themeId: string; subtopicId: string; term: string; definition: string } | undefined;
    await setupMeaningSnacksMock(page, {
      generate: (body) => {
        sent = body;
        return {
          status: 200,
          body: {
            alreadyExisted: false,
            meaningSnack: meaningSnack({
              sourceSubtopicId: body.subtopicId,
              sourceTerm: body.term,
              sourceDefinition: body.definition,
            }),
          },
        };
      },
    });
    await goToSnackCard(page);

    await defineTerm(page, '.snack-card__concept', 'chemical');
    await expect(page.locator(GENERATE_BUTTON)).toHaveText(/Create snack/i);
    await page.locator(GENERATE_BUTTON).click();

    await expect(page.locator(GENERATE_BUTTON)).toHaveText(/Added to your snacks/i, {
      timeout: 5000,
    });
    await expect(meaningIndexItems(page)).toHaveCount(1);

    expect(sent?.themeId).toBe('test-theme-001');
    expect(sent?.subtopicId).toBe('sub-001');
    expect(sent?.term).toBe('chemical');
    expect(sent?.definition).toBe(GENERATED_MEANING);
  });

  test('a delayed creation shows the Creating… loading state then done', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupMeaningSnacksMock(page, {
      generate: (body) => ({
        status: 200,
        delayMs: 800,
        body: {
          alreadyExisted: false,
          meaningSnack: meaningSnack({
            sourceSubtopicId: body.subtopicId,
            sourceTerm: body.term,
            sourceDefinition: body.definition,
          }),
        },
      }),
    });
    await goToSnackCard(page);

    await defineTerm(page, '.snack-card__concept', 'chemical');
    await page.locator(GENERATE_BUTTON).click();

    await expect(page.locator('.keyword-card__action-status')).toContainText(/Creating/i);
    await expect(page.locator(GENERATE_BUTTON)).toHaveText(/Added to your snacks/i, {
      timeout: 5000,
    });
    await expect(meaningIndexItems(page)).toHaveCount(1);
  });

  test('rapid double activation issues one request and creates one entry', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    let calls = 0;
    await setupMeaningSnacksMock(page, {
      generate: (body) => {
        calls += 1;
        return {
          status: 200,
          delayMs: 500,
          body: {
            alreadyExisted: false,
            meaningSnack: meaningSnack({
              sourceSubtopicId: body.subtopicId,
              sourceTerm: body.term,
              sourceDefinition: body.definition,
            }),
          },
        };
      },
    });
    await goToSnackCard(page);

    await defineTerm(page, '.snack-card__concept', 'chemical');
    await page.locator(GENERATE_BUTTON).evaluate((el) => {
      (el as HTMLElement).click();
      (el as HTMLElement).click();
    });

    await expect(page.locator(GENERATE_BUTTON)).toHaveText(/Added to your snacks/i, {
      timeout: 5000,
    });
    await expect(meaningIndexItems(page)).toHaveCount(1);
    expect(calls).toBe(1);
  });

  test('a failed creation shows a meaningful error and adds nothing', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupMeaningSnacksMock(page, {
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

    const before = await page.locator('.snack-index__item').count();
    await defineTerm(page, '.snack-card__concept', 'chemical');
    await page.locator(GENERATE_BUTTON).click();

    await expect(page.locator('.keyword-card__error')).toBeVisible();
    await expect(page.locator('.keyword-card__error')).toHaveAttribute('role', 'alert');
    await expect(page.locator('.keyword-card__error')).not.toHaveText('');
    await expect(meaningIndexItems(page)).toHaveCount(0);
    expect(await page.locator('.snack-index__item').count()).toBe(before);
  });

  test('re-defining the same term reflects done without a duplicate', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    let calls = 0;
    await setupMeaningSnacksMock(page, {
      generate: (body) => {
        calls += 1;
        return {
          status: 200,
          body: {
            alreadyExisted: false,
            meaningSnack: meaningSnack({
              sourceSubtopicId: body.subtopicId,
              sourceTerm: body.term,
              sourceDefinition: body.definition,
            }),
          },
        };
      },
    });
    await goToSnackCard(page);

    await defineTerm(page, '.snack-card__concept', 'chemical');
    await page.locator(GENERATE_BUTTON).click();
    await expect(page.locator(GENERATE_BUTTON)).toHaveText(/Added to your snacks/i, {
      timeout: 5000,
    });
    expect(calls).toBe(1);

    // Dismiss, then re-define the same term on the same entry.
    await page.keyboard.press('Escape');
    await expect(page.locator(CARD)).toHaveCount(0);
    await defineTerm(page, '.snack-card__concept', 'chemical');

    await expect(page.locator(GENERATE_BUTTON)).toHaveText(/Added to your snacks/i);
    await expect(page.locator(GENERATE_BUTTON)).toBeDisabled();
    await expect(meaningIndexItems(page)).toHaveCount(1);
    expect(calls).toBe(1);
  });

  test('a server-side alreadyExisted response resolves to done with one entry', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    // A fresh session: the client `hasGenerated` set is empty, so the card starts idle and a create
    // is actually issued. The backend reports an idempotent hit, so the flow must trust the server
    // response (done + exactly one entry) rather than the local generated-key set (FR-010/SC-002).
    let calls = 0;
    await setupMeaningSnacksMock(page, {
      generate: (body) => {
        calls += 1;
        return {
          status: 200,
          body: {
            alreadyExisted: true,
            meaningSnack: meaningSnack({
              id: 'm-idempotent',
              sourceSubtopicId: body.subtopicId,
              sourceTerm: body.term,
              sourceDefinition: body.definition,
            }),
          },
        };
      },
    });
    await goToSnackCard(page);

    await defineTerm(page, '.snack-card__concept', 'chemical');
    await page.locator(GENERATE_BUTTON).click();

    await expect(page.locator(GENERATE_BUTTON)).toHaveText(/Added to your snacks/i, {
      timeout: 5000,
    });
    await expect(page.locator(GENERATE_BUTTON)).toBeDisabled();
    await expect(meaningIndexItems(page)).toHaveCount(1);
    expect(calls).toBe(1);
  });

  test('the captured source subtopic is a meaning entry sourceSubtopicId (Option A)', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    let sent: { subtopicId: string } | undefined;
    await setupMeaningSnacksMock(page, {
      list: [meaningSnack({ id: 'm-seed', sourceTerm: 'chloroplast', sourceSubtopicId: 'sub-003' })],
      generate: (body) => {
        sent = body;
        return {
          status: 200,
          body: {
            alreadyExisted: false,
            meaningSnack: meaningSnack({
              id: 'm-created',
              sourceSubtopicId: body.subtopicId,
              sourceTerm: body.term,
              sourceDefinition: body.definition,
            }),
          },
        };
      },
    });
    await goToSnackCard(page);

    // Wait for the regular batch entry to render (the meaning listing can arrive first), so the
    // active index is stable before selecting the meaning entry.
    await expect(
      page.locator('.snack-index__item:not(.snack-index__item--meaning)'),
    ).toHaveCount(1);

    // Select the seeded meaning entry, then define a term from its content.
    await meaningIndexItems(page).click();
    await expect(page.locator('.snack-card--meaning')).toBeVisible();
    await defineTerm(page, '.snack-card--meaning .snack-card__concept', 'chemical');
    await page.locator(GENERATE_BUTTON).click();

    await expect(page.locator(GENERATE_BUTTON)).toHaveText(/Added to your snacks/i, {
      timeout: 5000,
    });
    expect(sent?.subtopicId).toBe('sub-003');
  });
});

// ─── US2: quiz-free & visual distinction ─────────────────────────────────────

test.describe('US2 — the created snack is distinct and quiz-free', () => {
  async function createMeaningSnack(page: Page): Promise<void> {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupMeaningSnacksMock(page, {
      generate: (body) => ({
        status: 200,
        body: {
          alreadyExisted: false,
          meaningSnack: meaningSnack({
            sourceSubtopicId: body.subtopicId,
            sourceTerm: body.term,
            sourceDefinition: body.definition,
          }),
        },
      }),
    });
    await goToSnackCard(page);
    await defineTerm(page, '.snack-card__concept', 'chemical');
    await page.locator(GENERATE_BUTTON).click();
    await expect(page.locator(GENERATE_BUTTON)).toHaveText(/Added to your snacks/i, {
      timeout: 5000,
    });
  }

  test('the created entry renders the distinct meaning card with no quiz button', async ({
    page,
  }) => {
    await createMeaningSnack(page);

    const regular = page.locator('.snack-card').first();
    const regularBorder = await regular.evaluate((el) => getComputedStyle(el).borderTopColor);

    await expect(meaningIndexItems(page)).toHaveCount(1);
    await meaningIndexItems(page).click();

    const meaningCard = page.locator('.snack-card--meaning');
    await expect(meaningCard).toBeVisible();
    await expect(meaningCard.locator('.snack-card__badge--meaning')).toHaveText(/Meaning snack/i);
    await expect(meaningCard.locator('.quiz-btn')).toHaveCount(0);

    const meaningBorder = await meaningCard.evaluate((el) => getComputedStyle(el).borderTopColor);
    expect(meaningBorder).not.toBe(regularBorder);
  });

  test('the regular snack keeps its quiz affordance and routes to /quiz', async ({ page }) => {
    await createMeaningSnack(page);

    await page.locator('.snack-index__item').first().click();
    const quizButton = page.locator('.snack-card .quiz-btn');
    await expect(quizButton).toBeVisible();
    await quizButton.click();
    await page.waitForURL('**/quiz');
  });

  test('walking every entry never lands on a blank card and offers no quiz for the meaning entry', async ({
    page,
  }) => {
    await createMeaningSnack(page);

    const items = page.locator('.snack-index__item');
    const count = await items.count();
    expect(count).toBeGreaterThan(1);

    for (let i = 0; i < count; i += 1) {
      await items.nth(i).click();
      const hasCard =
        (await page.locator('.snack-card, .snack-card--meaning').count()) > 0 ||
        (await page.locator('.error-banner, .loading.state').count()) > 0;
      expect(hasCard, `entry ${i} renders something`).toBe(true);
    }

    await meaningIndexItems(page).click();
    await expect(page.locator('.snack-card--meaning')).toBeVisible();
    await expect(page.locator('.quiz-btn')).toHaveCount(0);
  });

  test('selecting the created meaning entry issues no quiz request and stays on /snacks', async ({
    page,
  }) => {
    const quizRequests: string[] = [];
    page.on('request', (req) => {
      if (req.url().includes('/api/quiz')) {
        quizRequests.push(req.url());
      }
    });

    await createMeaningSnack(page);

    // Select the created meaning entry: no quiz affordance, no quiz navigation.
    await meaningIndexItems(page).click();
    await expect(page.locator('.snack-card--meaning')).toBeVisible();
    await expect(page.locator('.quiz-btn')).toHaveCount(0);
    await expect(page).toHaveURL(/\/snacks$/);

    // Select the regular entry too: rendering it must not auto-request a quiz either.
    await page.locator('.snack-index__item').first().click();
    await expect(page.locator('.snack-card .quiz-btn')).toBeVisible();
    await expect(page).toHaveURL(/\/snacks$/);

    await page.waitForTimeout(300);
    expect(quizRequests).toEqual([]);
  });
});

// ─── US3: persistence & retrieval ────────────────────────────────────────────

test.describe('US3 — persistence and graceful degradation', () => {
  test('a created meaning snack is re-listed in a new session for the same theme', async ({
    page,
  }) => {
    const stored: MeaningSnackFixture[] = [];
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupMeaningSnacksMock(page, {
      list: (themeId) => ({ status: 200, body: { themeId, meaningSnacks: stored } }),
      generate: (body) => {
        const created = meaningSnack({
          id: 'm-persisted',
          sourceSubtopicId: body.subtopicId,
          sourceTerm: body.term,
          sourceDefinition: body.definition,
        });
        stored.push(created);
        return { status: 200, body: { alreadyExisted: false, meaningSnack: created } };
      },
    });
    await goToSnackCard(page);

    await defineTerm(page, '.snack-card__concept', 'chemical');
    await page.locator(GENERATE_BUTTON).click();
    await expect(meaningIndexItems(page)).toHaveCount(1);

    // A new session (full page load, re-entering the same theme) re-lists the persisted snack.
    await goToSnackCard(page);
    await expect(meaningIndexItems(page)).toHaveCount(1);
    await meaningIndexItems(page).click();

    const meaningCard = page.locator('.snack-card--meaning');
    await expect(meaningCard).toBeVisible();
    await expect(meaningCard.locator('.snack-card__source-term')).toHaveText('chemical');
    await expect(meaningCard.locator('.snack-card__badge--meaning')).toHaveText(/Meaning snack/i);
    await expect(meaningCard.locator('.quiz-btn')).toHaveCount(0);
  });

  test('a failing listing still renders regular snacks with no crash or broken entry', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await setupMeaningSnacksMock(page, {
      list: () => ({ status: 500, body: { error: 'INTERNAL_ERROR', message: 'boom' } }),
    });
    await goToSnackCard(page);

    await expect(page.locator('.snack-card').first()).toBeVisible();
    await expect(meaningIndexItems(page)).toHaveCount(0);
    await expect(page.locator('.snack-card--meaning')).toHaveCount(0);
    await expect(page.locator('.error-banner')).toHaveCount(0);
  });
});

// ─── US4: security — untrusted text is escaped ───────────────────────────────

const XSS_PAYLOAD =
  '<script>window.__xss=1</script> <img src=x onerror="window.__xss=1"> ' +
  'javascript:window.__xss=1 <iframe></iframe> <style>body{color:red}</style>';

async function assertNoInjection(page: Page, scope: string): Promise<void> {
  expect(
    await page.evaluate(() => (window as unknown as { __xss?: number }).__xss),
  ).toBeUndefined();
  for (const selector of ['script', 'img', 'iframe', 'style', 'a']) {
    await expect(page.locator(`${scope} ${selector}`)).toHaveCount(0);
  }
}

test.describe('US4 — untrusted custom term, meaning and created content are inert', () => {
  test('a generated meaning containing payloads is inert and readable in the card', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupDefinitionsMock(page, {
      generate: (body) => ({
        status: 200,
        body: { term: body.term, definition: XSS_PAYLOAD },
      }),
    });
    await goToSnackCard(page);

    await defineTerm(page, '.snack-card__concept', 'chemical');

    const card = page.locator(CARD);
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__definition')).toContainText('<script>');
    await assertNoInjection(page, CARD);
  });

  test('created content containing payloads is inert in the backbone', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page);
    await setupMeaningSnacksMock(page, {
      generate: (body) => ({
        status: 200,
        body: {
          alreadyExisted: false,
          meaningSnack: meaningSnack({
            id: 'm-xss',
            sourceSubtopicId: body.subtopicId,
            sourceTerm: body.term,
            sourceDefinition: body.definition,
            concept: 'Payload <script>window.__xss=1</script> and readable concept text.',
            metaphor: 'A metaphor with <iframe></iframe> and <style>body{color:red}</style>.',
            whyItMatters: 'Inline style="color:red" is inert text here.',
          }),
        },
      }),
    });
    await goToSnackCard(page);

    await defineTerm(page, '.snack-card__concept', 'chemical');
    await page.locator(GENERATE_BUTTON).click();
    await expect(page.locator(GENERATE_BUTTON)).toHaveText(/Added to your snacks/i, {
      timeout: 5000,
    });

    await meaningIndexItems(page).click();
    await expect(page.locator('.snack-card--meaning')).toBeVisible();
    await assertNoInjection(page, '.snack-card--meaning');
    await expect(page.locator('.snack-card--meaning')).toContainText('readable concept text');
  });
});
