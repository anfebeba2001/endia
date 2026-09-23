import { test, expect, type Page } from '@playwright/test';
import {
  setupAllMocks,
  setupMeaningSnacksMock,
  meaningSnack,
  angularSubmitHome,
  setupThemesHistoryMock,
  type MeaningSnackFixture,
} from './helpers';
import type { ThemeHistory } from '../src/app/models/history.model';

/**
 * Feature 014 — "Generate snack" from a keyword definition into the snacks backbone (no quiz).
 *
 * Covers, through route-mocked fixtures:
 *  - US1 (FR-001..FR-006): the action, loading state, success (one entry), failure (no entry),
 *    rapid double-click guard, and the already-generated `done` state.
 *  - US2 (FR-010..FR-015): distinct rendering + always-present badge, no `.quiz-btn`, selecting a
 *    meaning entry never shows a blank card, and correct ordering / Previous-Next across a mixed
 *    list (first/middle/last + unmatched appended).
 *  - US3 (FR-018/FR-020): persistence across reload and graceful degradation when the listing
 *    fails/returns nothing.
 *  - US4 (FR-021..FR-023): injection payloads in source/generated content and in definition cards
 *    on the snacks, results and history surfaces execute nothing and inject no nodes.
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

const KEYWORD = { term: 'photosynthesis', definition: 'light to sugar' };

const KEYWORD_SNACK: FixtureSnack = {
  id: 'snack-kw-1',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: 'Photosynthesis converts light into chemical energy.',
  metaphor: 'Think of a leaf as a solar kitchen.',
  whyItMatters: 'Nearly every food chain starts here.',
  related: ['Chlorophyll', 'Glucose'],
  keywords: [KEYWORD],
};

/** Two keywords on one recap concept, for the results-surface stale-error regression. */
const TWO_KEYWORD_SNACK: FixtureSnack = {
  id: 'snack-two-kw',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: 'Photosynthesis uses Chlorophyll to convert light into chemical energy.',
  metaphor: 'Think of a leaf as a solar kitchen.',
  whyItMatters: 'Nearly every food chain starts here.',
  related: ['Glucose'],
  keywords: [KEYWORD, { term: 'chlorophyll', definition: 'the green pigment' }],
};

// ─── Fixture helpers ─────────────────────────────────────────────────────────

async function overrideBatchWithSnack(page: Page, snack: FixtureSnack): Promise<void> {
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

async function overrideBatchWithTwoKeywordSnacks(page: Page): Promise<void> {
  const items = [
    {
      subtopicId: 'sub-001',
      subtopicName: 'Light reactions',
      concept: 'Photosynthesis converts light into chemical energy.',
      keyword: KEYWORD,
    },
    {
      subtopicId: 'sub-002',
      subtopicName: 'Calvin cycle',
      concept: 'The Calvin cycle fixes carbon into sugar.',
      keyword: { term: 'carbon', definition: 'building block' },
    },
  ];
  await page.route('**/api/batches/*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        batchId: 'test-batch-001',
        themeId: 'test-theme-001',
        status: 'COMPLETE',
        retrying: false,
        items: items.map((item, i) => ({
          subtopicId: item.subtopicId,
          subtopicName: item.subtopicName,
          snackStatus: 'READY',
          snack: {
            id: `snack-kw-${i + 1}`,
            subtopicId: item.subtopicId,
            subtopicName: item.subtopicName,
            concept: item.concept,
            metaphor: 'A metaphor.',
            whyItMatters: 'It matters.',
            related: ['Related'],
            keywords: [item.keyword],
          },
          snackCause: null,
          quizStatus: 'READY',
          quizCause: null,
        })),
      }),
    }),
  );
}

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

async function goToSnackCard(page: Page): Promise<void> {
  await page.goto('/');
  await page.waitForTimeout(1500);
  await angularSubmitHome(page, 'Photosynthesis');
  await page.waitForURL('**/subtopics');
  await page.click('button.primary-btn');
  await page.waitForURL('**/snacks');
  await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });
}

async function openDefinitionCard(page: Page, term = KEYWORD.term): Promise<void> {
  const termControl = page
    .locator(`.snack-card__concept .rich-text__keyword[data-term="${term}"]`)
    .first();
  await termControl.evaluate((el) => el.scrollIntoView({ block: 'center' }));
  await termControl.click();
  await expect(page.locator('.keyword-card')).toBeVisible();
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

const meaningIndexItems = (page: Page) => page.locator('.snack-index__item--meaning');

// ─── US1: action, loading, success, failure, duplicate, done ─────────────────

test.describe('US1 — Generate snack action', () => {
  test('every open definition card shows a labelled Generate snack action', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, KEYWORD_SNACK);
    await goToSnackCard(page);
    await openDefinitionCard(page);

    const button = page.locator('.keyword-card__generate');
    await expect(button).toBeVisible();
    await expect(button).toHaveText(/Generate snack/i);
  });

  test('shows a loading state, then adds exactly one meaning entry on success', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, KEYWORD_SNACK);
    await setupMeaningSnacksMock(page, {
      generate: (body) => ({
        status: 200,
        delayMs: 700,
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
    await openDefinitionCard(page);

    await expect(meaningIndexItems(page)).toHaveCount(0);
    await page.locator('.keyword-card__generate').click();

    await expect(page.locator('.keyword-card__action-status')).toContainText(/Generating/i);
    await expect(page.locator('.keyword-card__generate')).toHaveText(/Added to your snacks/i, {
      timeout: 5000,
    });
    await expect(meaningIndexItems(page)).toHaveCount(1);
  });

  test('a failed generation shows an error and adds no entry', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, KEYWORD_SNACK);
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
    await openDefinitionCard(page);

    const before = await page.locator('.snack-index__item').count();
    await page.locator('.keyword-card__generate').click();

    await expect(page.locator('.keyword-card__error')).toBeVisible();
    await expect(meaningIndexItems(page)).toHaveCount(0);
    expect(await page.locator('.snack-index__item').count()).toBe(before);
  });

  test('rapid double activation issues one request and creates no duplicate', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, KEYWORD_SNACK);
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
    await openDefinitionCard(page);

    await page.locator('.keyword-card__generate').evaluate((el) => {
      (el as HTMLElement).click();
      (el as HTMLElement).click();
    });

    await expect(page.locator('.keyword-card__generate')).toHaveText(/Added to your snacks/i, {
      timeout: 5000,
    });
    await expect(meaningIndexItems(page)).toHaveCount(1);
    expect(calls).toBe(1);
  });

  test('reopening the card for an already-generated keyword shows the done state', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, KEYWORD_SNACK);
    await setupMeaningSnacksMock(page);
    await goToSnackCard(page);
    await openDefinitionCard(page);

    await page.locator('.keyword-card__generate').click();
    await expect(page.locator('.keyword-card__generate')).toHaveText(/Added to your snacks/i, {
      timeout: 5000,
    });

    await page.keyboard.press('Escape');
    await expect(page.locator('.keyword-card')).toHaveCount(0);
    await openDefinitionCard(page);

    const button = page.locator('.keyword-card__generate');
    await expect(button).toHaveText(/Added to your snacks/i);
    await expect(button).toBeDisabled();
    await expect(meaningIndexItems(page)).toHaveCount(1);
  });
});

// ─── US2: distinct rendering + no quiz ───────────────────────────────────────

test.describe('US2 — meaning snacks are distinct and quiz-free', () => {
  test('a meaning entry renders a distinct card + badge with no quiz button', async ({ page }) => {
    await setupAllMocks(page);
    await setupMeaningSnacksMock(page, { list: [meaningSnack({ sourceTerm: 'photosynthesis' })] });
    await goToSnackCard(page);

    await expect(meaningIndexItems(page)).toHaveCount(1);
    await expect(meaningIndexItems(page).locator('.snack-index__badge')).toHaveText(/Meaning/i);

    await meaningIndexItems(page).click();
    const meaningCard = page.locator('.snack-card--meaning');
    await expect(meaningCard).toBeVisible();
    await expect(meaningCard.locator('.snack-card__badge--meaning')).toHaveText(/Meaning snack/i);
    await expect(meaningCard.locator('.quiz-btn')).toHaveCount(0);
    await expect(meaningCard.locator('.snack-card__source-term')).toHaveText('photosynthesis');
    await expect(meaningCard).toContainText('Photosynthesis');
  });

  test('a meaning card is colour-distinct from a regular card, which keeps its quiz button', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await setupMeaningSnacksMock(page, { list: [meaningSnack({ sourceTerm: 'photosynthesis' })] });
    await goToSnackCard(page);

    const regular = page.locator('.snack-card').first();
    await expect(regular.locator('.quiz-btn')).toBeVisible();
    const regularBorder = await regular.evaluate((el) => getComputedStyle(el).borderTopColor);

    await meaningIndexItems(page).click();
    const meaningCard = page.locator('.snack-card--meaning');
    await expect(meaningCard).toBeVisible();
    const meaningBorder = await meaningCard.evaluate((el) => getComputedStyle(el).borderTopColor);

    expect(meaningBorder).not.toBe(regularBorder);
  });

  test('selecting a meaning entry never renders a blank card', async ({ page }) => {
    await setupAllMocks(page);
    await setupMeaningSnacksMock(page, { list: [meaningSnack({ sourceTerm: 'photosynthesis' })] });
    await goToSnackCard(page);

    await meaningIndexItems(page).click();
    await expect(page.locator('.snack-card--meaning')).toBeVisible();
    await expect(page.locator('.snack-card--meaning')).toContainText('chemical energy');
  });
});

// ─── US2: ordering across a mixed list ───────────────────────────────────────

test.describe('US2 — mixed ordering, Previous/Next and disabled states', () => {
  test('interleaves after the source subtopic, appends unmatched, and never lands blank', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await setupMeaningSnacksMock(page, {
      list: [
        meaningSnack({ id: 'm-first', sourceTerm: 'first', sourceSubtopicId: 'sub-001' }),
        meaningSnack({ id: 'm-last', sourceTerm: 'last', sourceSubtopicId: 'sub-003' }),
        meaningSnack({
          id: 'm-unmatched',
          sourceTerm: 'unmatched',
          sourceSubtopicId: 'sub-999',
          sourceSubtopicName: 'Elsewhere',
        }),
      ],
    });
    await goToSnackCard(page);

    const items = page.locator('.snack-index__item');
    await expect(items).toHaveCount(6); // 3 regular + 3 meaning
    await expect(meaningIndexItems(page)).toHaveCount(3);
    // Meaning for sub-001 sits right after the first regular entry.
    await expect(items.nth(1)).toHaveClass(/snack-index__item--meaning/);
    // Meaning for sub-003 sits right after the third regular entry.
    await expect(items.nth(4)).toHaveClass(/snack-index__item--meaning/);
    // Unmatched meaning appended at the end.
    await expect(items.nth(5)).toHaveClass(/snack-index__item--meaning/);

    // Previous is disabled at the first position.
    await expect(page.locator('.nav-btn--prev')).toBeDisabled();

    // Walk every entry: selecting any one never renders a blank card.
    for (let i = 0; i < 6; i += 1) {
      await items.nth(i).click();
      const hasCard =
        (await page.locator('.snack-card, .snack-card--meaning').count()) > 0 ||
        (await page.locator('.error-banner, .loading.state').count()) > 0;
      expect(hasCard, `entry ${i} renders something`).toBe(true);
    }

    // Move to the last entry: Next becomes disabled.
    await items.nth(5).click();
    await expect(page.locator('.nav-btn--next')).toBeDisabled();
    await expect(page.locator('.snack-card--meaning')).toContainText('unmatched');
  });
});

// ─── US3: persistence across reload + graceful degradation ───────────────────

test.describe('US3 — persistence and graceful degradation', () => {
  test('a generated meaning snack is re-listed in a new session for the same theme', async ({
    page,
  }) => {
    const stored: MeaningSnackFixture[] = [];
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, KEYWORD_SNACK);
    await setupMeaningSnacksMock(page, {
      list: (themeId) => ({ status: 200, body: { themeId, meaningSnacks: stored } }),
      generate: (body) => {
        const created = meaningSnack({
          sourceSubtopicId: body.subtopicId,
          sourceTerm: body.term,
          sourceDefinition: body.definition,
        });
        stored.push(created);
        return { status: 200, body: { alreadyExisted: false, meaningSnack: created } };
      },
    });
    await goToSnackCard(page);
    await openDefinitionCard(page);
    await page.locator('.keyword-card__generate').click();
    await expect(meaningIndexItems(page)).toHaveCount(1);

    // A new session (full page load, re-entering the same theme): the meaning snack is re-listed
    // from GET /api/meaning-snacks and keeps its distinct, quiz-free treatment (FR-018/SC-006).
    await goToSnackCard(page);
    await expect(meaningIndexItems(page)).toHaveCount(1);

    await meaningIndexItems(page).click();
    await expect(page.locator('.snack-card--meaning')).toBeVisible();
    await expect(page.locator('.snack-card--meaning .quiz-btn')).toHaveCount(0);
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

  test('an empty listing renders exactly the regular snacks', async ({ page }) => {
    await setupAllMocks(page);
    await setupMeaningSnacksMock(page, { list: [] });
    await goToSnackCard(page);

    await expect(page.locator('.snack-card').first()).toBeVisible();
    await expect(meaningIndexItems(page)).toHaveCount(0);
  });
});

// ─── US4: security — injected markup is inert on every surface ───────────────

const XSS_TERM = '<script>window.__xss=1</script>';
const XSS_DEFINITION = '<img src=x onerror="window.__xss=1">';

const XSS_MEANING = meaningSnack({
  id: 'm-xss',
  sourceTerm: XSS_TERM,
  sourceDefinition: XSS_DEFINITION,
  concept: 'Payload <script>window.__xss=1</script> and readable concept text.',
  metaphor: 'A metaphor with <iframe></iframe> and <style>body{color:red}</style> inside.',
  whyItMatters: 'Inline style="color:red" is inert text here.',
});

const XSS_KEYWORD_SNACK: FixtureSnack = {
  id: 'snack-xss',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: '<script>window.__xss=1</script> Safe concept text.',
  metaphor: 'Plain metaphor.',
  whyItMatters: 'Plain why.',
  related: ['Plain', 'Labels'],
  keywords: [{ term: XSS_TERM, definition: XSS_DEFINITION }],
};

async function assertNoInjection(page: Page, scope: string): Promise<void> {
  expect(
    await page.evaluate(() => (window as unknown as { __xss?: number }).__xss),
  ).toBeUndefined();
  for (const selector of ['script', 'img', 'iframe', 'style', 'a']) {
    await expect(page.locator(`${scope} ${selector}`)).toHaveCount(0);
  }
}

test.describe('US4 — untrusted meaning/keyword text is escaped', () => {
  test('meaning-snack content payloads are inert and readable text is preserved', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await setupMeaningSnacksMock(page, { list: [XSS_MEANING] });
    await goToSnackCard(page);

    await meaningIndexItems(page).click();
    await expect(page.locator('.snack-card--meaning')).toBeVisible();

    await assertNoInjection(page, '.snack-card--meaning');
    await expect(page.locator('.snack-card--meaning')).toContainText('readable concept text');
  });

  test('the definition card on the snacks surface escapes its term/definition', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, XSS_KEYWORD_SNACK);
    await goToSnackCard(page);
    await openDefinitionCard(page, XSS_TERM);

    const card = page.locator('.keyword-card');
    await expect(card.locator('.keyword-card__definition')).toContainText('<img');
    await assertNoInjection(page, '.keyword-card');
  });

  test('the results recap definition card escapes the same payloads', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, XSS_KEYWORD_SNACK);
    await overrideSummaryWithRecap(page, XSS_KEYWORD_SNACK);
    await goToSnackCard(page);
    await completeQuizToResults(page);

    await page.locator('.recap-card__concept .rich-text__keyword').first().click();
    const card = page.locator('.keyword-card');
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__definition')).toContainText('<img');
    await assertNoInjection(page, '.keyword-card');
  });

  test('the history definition card escapes the same payloads', async ({ page }) => {
    const themes: ThemeHistory[] = [
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
                concept: '<script>window.__xss=1</script> Safe concept text.',
                metaphor: 'Plain metaphor.',
                whyItMatters: 'Plain why.',
                related: ['Plain', 'Labels'],
                keywords: [{ term: XSS_TERM, definition: XSS_DEFINITION }],
              },
            ],
            quizQuestions: [],
          },
        ],
      },
    ];
    await setupAllMocks(page);
    await setupThemesHistoryMock(page, themes);
    await page.goto('/history');
    await page.waitForTimeout(1500);

    await page.locator('.history-card__toggle').first().click();
    const term = page.locator('.history-snack__concept .rich-text__keyword').first();
    await term.evaluate((el) => el.scrollIntoView({ block: 'center' }));
    await term.click();

    const card = page.locator('.keyword-card');
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__definition')).toContainText('<img');
    await assertNoInjection(page, '.keyword-card');
  });
});

// ─── Robustness: unicode/RTL, escaped entities, very long content ────────────

test.describe('US4 — robustness of meaning-snack rendering', () => {
  test('unicode, emoji and RTL text render readably', async ({ page }) => {
    await setupAllMocks(page);
    await setupMeaningSnacksMock(page, {
      list: [
        meaningSnack({
          id: 'm-unicode',
          sourceTerm: '光合作用 🍃',
          sourceDefinition: 'ضوء إلى سكر',
          concept: 'Unicode 🎉 and RTL: محتوى عربي. Japanese: 日本語.',
          metaphor: 'Emoji 🍃 in a metaphor.',
          whyItMatters: 'Readable in any direction.',
        }),
      ],
    });
    await goToSnackCard(page);
    await meaningIndexItems(page).click();

    const card = page.locator('.snack-card--meaning');
    await expect(card).toBeVisible();
    await expect(card).toContainText('🍃');
    await expect(card).toContainText('🎉');
    await expect(card).toContainText('日本語');
    await expect(card).toContainText('محتوى عربي');
  });

  test('escaped entities are shown as intended characters without double-escaping', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await setupMeaningSnacksMock(page, {
      list: [
        meaningSnack({
          id: 'm-entities',
          sourceTerm: '&lt;b&gt;not bold&lt;/b&gt;',
          sourceDefinition: '&amp;amp; entity',
          concept: 'Escaped entities: &lt;tag&gt; and &amp;.',
        }),
      ],
    });
    await goToSnackCard(page);
    await meaningIndexItems(page).click();

    const card = page.locator('.snack-card--meaning');
    await expect(card).toBeVisible();
    // The rich-text renderer decodes entities to the intended characters (feature-012 model) with
    // no double-escaping, and no real element is ever created from them.
    await expect(card).toContainText('<tag>');
    await expect(card).toContainText('&');
    await expect(card.locator('b')).toHaveCount(0);
    await expect(card.locator('tag')).toHaveCount(0);
    // The source term is shown verbatim as the stored characters.
    await expect(card.locator('.snack-card__source-term')).toContainText('&lt;b&gt;');
  });

  test('very long generated content keeps the layout stable with no horizontal overflow', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await setupMeaningSnacksMock(page, {
      list: [
        meaningSnack({
          id: 'm-long',
          sourceTerm: 'verylongunbrokentoken'.repeat(20),
          concept: `${'word '.repeat(600)}END`,
          metaphor: 'short',
          whyItMatters: 'short',
        }),
      ],
    });
    await goToSnackCard(page);
    await meaningIndexItems(page).click();
    await expect(page.locator('.snack-card--meaning')).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > window.innerWidth + 1,
    );
    expect(overflow, 'no horizontal overflow with very long content').toBe(false);
  });
});

// ─── US1: the action is offered on all three definition-card surfaces ────────

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
            concept: '**Photosynthesis** converts light into chemical energy.',
            metaphor: 'Think of a leaf as a solar kitchen.',
            whyItMatters: 'Nearly every food chain starts here.',
            related: ['Chlorophyll', 'Glucose'],
            keywords: [KEYWORD],
          },
        ],
        quizQuestions: [],
      },
    ],
  },
];

test.describe('US1 — the action is offered on every definition-card surface', () => {
  test('the results recap card offers Generate snack and generates', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, KEYWORD_SNACK);
    await overrideSummaryWithRecap(page, KEYWORD_SNACK);
    await setupMeaningSnacksMock(page);
    await goToSnackCard(page);
    await completeQuizToResults(page);

    await page.locator('.recap-card__concept .rich-text__keyword').first().click();
    const button = page.locator('.keyword-card__generate');
    await expect(button).toBeVisible();
    await expect(button).toHaveText(/Generate snack/i);

    await button.click();
    await expect(button).toHaveText(/Added to your snacks/i, { timeout: 5000 });
  });

  test('the history card offers Generate snack and generates', async ({ page }) => {
    await setupAllMocks(page);
    await setupThemesHistoryMock(page, HISTORY_THEME);
    await setupMeaningSnacksMock(page);
    await page.goto('/history');
    await page.waitForTimeout(1500);

    await page.locator('.history-card__toggle').first().click();
    const term = page.locator('.history-snack__concept .rich-text__keyword').first();
    await term.evaluate((el) => el.scrollIntoView({ block: 'center' }));
    await term.click();

    const button = page.locator('.keyword-card__generate');
    await expect(button).toBeVisible();
    await expect(button).toHaveText(/Generate snack/i);

    await button.click();
    await expect(button).toHaveText(/Added to your snacks/i, { timeout: 5000 });
  });
});

// ─── US1: idempotency (FR-005/FR-006, SC-002) ────────────────────────────────

test.describe('US1 — idempotency: no duplicates, no redundant generation', () => {
  test('re-generating the same keyword never duplicates or re-requests', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, KEYWORD_SNACK);
    let calls = 0;
    await setupMeaningSnacksMock(page, {
      list: [],
      generate: (body) => {
        calls += 1;
        return {
          status: 200,
          body: {
            // The backend reports an idempotent hit on any repeat (FR-006).
            alreadyExisted: calls > 1,
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
    await openDefinitionCard(page);

    await page.locator('.keyword-card__generate').click();
    await expect(page.locator('.keyword-card__generate')).toHaveText(/Added to your snacks/i, {
      timeout: 5000,
    });
    await expect(meaningIndexItems(page)).toHaveCount(1);
    expect(calls).toBe(1);

    // Re-opening the card reflects the done state and issues no further generation request.
    await page.keyboard.press('Escape');
    await expect(page.locator('.keyword-card')).toHaveCount(0);
    await openDefinitionCard(page);
    await expect(page.locator('.keyword-card__generate')).toBeDisabled();
    await expect(meaningIndexItems(page)).toHaveCount(1);
    expect(calls).toBe(1);
  });

  test('a meaning snack already in the listing reflects done without generating', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, KEYWORD_SNACK);
    let calls = 0;
    await setupMeaningSnacksMock(page, {
      list: [meaningSnack({ id: 'm-listed', sourceTerm: 'photosynthesis' })],
      generate: () => {
        calls += 1;
        return {
          status: 200,
          body: {
            alreadyExisted: true,
            meaningSnack: meaningSnack({ id: 'm-listed', sourceTerm: 'photosynthesis' }),
          },
        };
      },
    });
    await goToSnackCard(page);
    await openDefinitionCard(page);

    const button = page.locator('.keyword-card__generate');
    await expect(button).toHaveText(/Added to your snacks/i);
    await expect(button).toBeDisabled();
    // The listing alone marks the keyword generated; no POST is issued by merely opening the card.
    expect(calls).toBe(0);
    await expect(meaningIndexItems(page)).toHaveCount(1);
  });
});

// ─── US2: distinct in dark mode, and never treated as quiz-bearing ───────────

test.describe('US2 — distinct and quiz-free in the dark theme', () => {
  test('a meaning entry stays visually distinct and quiz-free in dark mode', async ({ page }) => {
    await setupAllMocks(page);
    await setupMeaningSnacksMock(page, { list: [meaningSnack({ sourceTerm: 'photosynthesis' })] });
    await goToSnackCard(page);

    // Switch to the dark theme (FR-012 requires the distinction in both themes).
    await page.click('button.theme-toggle');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

    const regular = page.locator('.snack-card').first();
    await expect(regular.locator('.quiz-btn')).toBeVisible();
    const regularBorder = await regular.evaluate((el) => getComputedStyle(el).borderTopColor);

    await expect(meaningIndexItems(page).locator('.snack-index__badge')).toBeVisible();
    await meaningIndexItems(page).click();

    const meaningCard = page.locator('.snack-card--meaning');
    await expect(meaningCard).toBeVisible();
    await expect(meaningCard.locator('.snack-card__badge--meaning')).toHaveText(/Meaning snack/i);
    await expect(meaningCard.locator('.quiz-btn')).toHaveCount(0);

    const meaningBorder = await meaningCard.evaluate((el) => getComputedStyle(el).borderTopColor);
    expect(meaningBorder).not.toBe(regularBorder);
  });

  test('a meaning entry offers no quiz while the regular snack keeps its Start Quiz', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await setupMeaningSnacksMock(page, { list: [meaningSnack({ sourceTerm: 'photosynthesis' })] });
    await goToSnackCard(page);

    // The meaning entry exposes no quiz affordance anywhere on the page.
    await meaningIndexItems(page).click();
    await expect(page.locator('.snack-card--meaning')).toBeVisible();
    await expect(page.locator('.quiz-btn')).toHaveCount(0);

    // The regular entry keeps its quiz affordance and still routes to the quiz.
    await page.locator('.snack-index__item').first().click();
    const regularQuiz = page.locator('.snack-card .quiz-btn');
    await expect(regularQuiz).toBeVisible();
    await regularQuiz.click();
    await page.waitForURL('**/quiz');
  });
});

// ─── QA remediation: stale error, keyboard-nav source, reset state ───────────

test.describe('QA remediation — parity and state hygiene', () => {
  test('the results card clears a stale generation error when another keyword is activated', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, KEYWORD_SNACK);
    await overrideSummaryWithRecap(page, TWO_KEYWORD_SNACK);
    await setupMeaningSnacksMock(page, {
      generate: () => ({
        status: 503,
        body: {
          error: 'UNAVAILABLE',
          message: 'The AI service is unavailable.',
          cause: 'UNAVAILABLE',
        },
      }),
    });
    await goToSnackCard(page);
    await completeQuizToResults(page);

    // Open the first keyword and let the generation fail (inline error shown).
    await page
      .locator('.recap-card__concept .rich-text__keyword[data-term="photosynthesis"]')
      .first()
      .click();
    await page.locator('.keyword-card__generate').click();
    await expect(page.locator('.keyword-card__error')).toBeVisible();

    // Activate the second keyword without a pointerdown (keyboard-equivalent): the stale error must
    // not linger on the reopened card (results parity with snacks/history).
    await page
      .locator('.recap-card__concept .rich-text__keyword[data-term="chlorophyll"]')
      .first()
      .evaluate((el) => (el as HTMLElement).click());

    await expect(page.locator('.keyword-card__term')).toHaveText('chlorophyll');
    await expect(page.locator('.keyword-card__error')).toHaveCount(0);
  });

  test('keyboard Next dismisses an open definition card so no snack attaches to the wrong subtopic', async ({
    page,
  }) => {
    await setupAllMocks(page);
    await overrideBatchWithTwoKeywordSnacks(page);
    await setupMeaningSnacksMock(page);
    await goToSnackCard(page);

    // Open the card on the first entry's keyword.
    await page
      .locator('.snack-card__concept .rich-text__keyword[data-term="photosynthesis"]')
      .first()
      .click();
    await expect(page.locator('.keyword-card')).toBeVisible();

    // Keyboard-activate Next (no pointerdown, so only the navigation guard can dismiss the card).
    await page.locator('.nav-btn--next').focus();
    await page.keyboard.press('Enter');

    await expect(page.locator('.keyword-card')).toHaveCount(0);
    await expect(page.locator('.snack-card__concept')).toContainText('Calvin cycle');
  });

  test('restart clears generated-key state so a re-entered theme starts idle', async ({ page }) => {
    await setupAllMocks(page);
    await overrideBatchWithSnack(page, KEYWORD_SNACK);
    // The listing never returns the generated snack, so `done` can only come from in-memory state.
    await setupMeaningSnacksMock(page, { list: [] });
    await goToSnackCard(page);
    await openDefinitionCard(page);
    await page.locator('.keyword-card__generate').click();
    await expect(page.locator('.keyword-card__generate')).toHaveText(/Added to your snacks/i);
    await page.keyboard.press('Escape');

    // Reach results and restart (AppState + generated keys are reset).
    await completeQuizToResults(page);
    await page.click('.results-footer .primary-btn');
    await page.waitForURL('/');
    await page.waitForTimeout(500);

    // Re-enter the same theme: without the reset the card would still show the stale "done" state.
    await angularSubmitHome(page, 'Photosynthesis');
    await page.waitForURL('**/subtopics');
    await page.click('button.primary-btn');
    await page.waitForURL('**/snacks');
    await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });
    await openDefinitionCard(page);

    await expect(page.locator('.keyword-card__generate')).toHaveText(/Generate snack/i);
    await expect(page.locator('.keyword-card__generate')).toBeEnabled();
  });
});
