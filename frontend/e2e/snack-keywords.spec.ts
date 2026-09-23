import { test, expect, type Page } from '@playwright/test';
import { setupAllMocks, angularSubmitHome } from './helpers';

/**
 * Feature 013 — colored keywords with click-to-show definition cards.
 *
 * Covers, through route-mocked fixtures:
 *  - US2 (FR-013..FR-017): every occurrence of a present term is highlighted in a distinct color;
 *    absent terms produce no artifact; matching is case-insensitive, literal, whole-word,
 *    longest-wins/non-overlapping; a term inside `**bold**` is both emphasized and highlighted;
 *    `related` is never highlighted.
 *  - US3 (FR-018..FR-023): click/tap opens a card with only the ≤3-word definition; outside-click,
 *    the close control and Escape dismiss it without navigation; opening a different term closes
 *    the previous card; keyboard activation works and Escape returns focus; desktop + mobile.
 *  - US4 (FR-026..FR-029): untrusted keyword term/definition payloads execute nothing, inject no
 *    nodes and preserve readable text; the card's definition is escaped.
 *  - US5 (FR-024, SC-006): empty/absent/not-present keywords render exactly as before.
 *  - Cross-surface (FR-025): the snack card and the results recap render consistently.
 *
 * The app renders snacks from the batch endpoints (`POST /api/batches` + `GET /api/batches/{id}`),
 * so the batch status route is overridden per test (mirroring `snack-rich-formatting.spec.ts`).
 */

interface Keyword {
  term: string;
  definition: string;
}

interface FixtureSnack {
  id: string;
  subtopicId: string;
  subtopicName: string;
  concept: string;
  metaphor: string;
  whyItMatters: string;
  related: string[];
  keywords?: Keyword[];
}

const KEYWORD_SNACK: FixtureSnack = {
  id: 'snack-kw-1',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept:
    'Photosynthesis converts light into chemical energy. photosynthesis also releases oxygen.',
  metaphor: 'Think of a leaf as a solar kitchen that cooks sugar.',
  whyItMatters: 'Plants build themselves from light, and light drives everything.',
  related: ['Chlorophyll', 'Glucose'],
  keywords: [
    { term: 'photosynthesis', definition: 'light to sugar' },
    { term: 'solar kitchen', definition: 'leaf factory' },
    { term: 'light', definition: 'electromagnetic energy' },
    { term: 'chlorophyll', definition: 'green pigment' },
  ],
};

const OVERLAP_SNACK: FixtureSnack = {
  id: 'snack-overlap',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: 'The light reactions capture energy first.',
  metaphor: 'Plain metaphor.',
  whyItMatters: 'Plain why.',
  related: ['One', 'Two'],
  keywords: [
    { term: 'light', definition: 'electromagnetic energy' },
    { term: 'light reactions', definition: 'energy capture' },
  ],
};

const BOLD_SNACK: FixtureSnack = {
  id: 'snack-bold',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: '**Photosynthesis** is the core process.',
  metaphor: 'Plain metaphor.',
  whyItMatters: 'Plain why.',
  related: ['One', 'Two'],
  keywords: [{ term: 'photosynthesis', definition: 'light to sugar' }],
};

const REGEX_SNACK: FixtureSnack = {
  id: 'snack-regex',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: 'C++ and (x) and a.b are literal terms here.',
  metaphor: 'Plain metaphor.',
  whyItMatters: 'Plain why.',
  related: ['One', 'Two'],
  keywords: [
    { term: 'C++', definition: 'language' },
    { term: '(x)', definition: 'function call' },
    { term: 'a.b', definition: 'dotted path' },
  ],
};

const EMPTY_KEYWORDS_SNACK: FixtureSnack = {
  id: 'snack-empty',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: 'Plain concept with no highlighted terms at all.',
  metaphor: 'Plain metaphor.',
  whyItMatters: 'Plain why.',
  related: ['One', 'Two'],
  keywords: [],
};

const ABSENT_KEYWORDS_SNACK: FixtureSnack = {
  id: 'snack-absent',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: 'Plain concept without a keywords field.',
  metaphor: 'Plain metaphor.',
  whyItMatters: 'Plain why.',
  related: ['One', 'Two'],
};

const LARGE_KEYWORDS_SNACK: FixtureSnack = {
  id: 'snack-large',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: 'Word0 Word1 Word2 Word3 Word4 Word5 Word6 Word7 Word8 Word9 end.',
  metaphor: 'Plain metaphor.',
  whyItMatters: 'Plain why.',
  related: ['One', 'Two'],
  keywords: Array.from({ length: 40 }, (_, i) => ({
    term: `Word${i}`,
    definition: `def ${i}`,
  })),
};

const XSS_SNACK: FixtureSnack = {
  id: 'snack-xss',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: '<script>window.__xss=1</script> Safe concept text.',
  metaphor: '<img src=x onerror="window.__xss=1"> Metaphor text.',
  whyItMatters: 'Why text with <iframe></iframe> and <style>body{color:red}</style> inside.',
  related: ['Plain', 'Labels'],
  keywords: [
    { term: '<script>window.__xss=1</script>', definition: '<img src=x onerror="window.__xss=1">' },
    { term: '<img src=x onerror="window.__xss=1">', definition: 'javascript:window.__xss=1' },
    { term: '<iframe></iframe>', definition: '<style>body{color:red}</style>' },
  ],
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
            subtopicId: 'sub-001',
            subtopicName: 'Light reactions',
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

async function setupFixtures(page: Page, snack: FixtureSnack = KEYWORD_SNACK): Promise<void> {
  await setupAllMocks(page);
  await overrideBatchWithSnack(page, snack);
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

/** Tab until `selector` matches the active element (keyboard-driven focus). */
async function tabUntilFocused(page: Page, selector: string, maxTabs = 40): Promise<boolean> {
  for (let i = 0; i < maxTabs; i++) {
    await page.keyboard.press('Tab');
    const matched = await page.evaluate(
      (sel) => document.activeElement?.matches(sel) ?? false,
      selector,
    );
    if (matched) {
      return true;
    }
  }
  return false;
}

const keywordIn = (page: Page, scope: string, term: string) =>
  page.locator(`${scope} .rich-text__keyword[data-term="${term}"]`);

// ─── US2: highlighting ────────────────────────────────────────────────────────

test.describe('US2 — key terms are highlighted in a distinct color', () => {
  test('every occurrence of a present term is highlighted, case-insensitively, original casing kept', async ({
    page,
  }) => {
    await setupFixtures(page);
    await goToSnackCard(page);

    const matches = keywordIn(page, '.snack-card__concept', 'photosynthesis');
    await expect(matches).toHaveCount(2);
    await expect(matches.nth(0)).toHaveText('Photosynthesis');
    await expect(matches.nth(1)).toHaveText('photosynthesis');
  });

  test('the highlight color is distinct from the body text color', async ({ page }) => {
    await setupFixtures(page);
    await goToSnackCard(page);

    const keywordColor = await keywordIn(page, '.snack-card__concept', 'photosynthesis')
      .first()
      .evaluate((el) => getComputedStyle(el).color);
    const bodyColor = await page
      .locator('.snack-card__concept .rich-text__p')
      .first()
      .evaluate((el) => getComputedStyle(el).color);

    expect(keywordColor).not.toBe(bodyColor);
  });

  test('a term absent from the body produces no highlight artifact', async ({ page }) => {
    await setupFixtures(page);
    await goToSnackCard(page);

    await expect(keywordIn(page, '.snack-card__concept', 'chlorophyll')).toHaveCount(0);
    await expect(keywordIn(page, '.snack-card__metaphor', 'chlorophyll')).toHaveCount(0);
  });

  test('overlapping terms highlight only the longer term with no nested controls', async ({
    page,
  }) => {
    await setupFixtures(page, OVERLAP_SNACK);
    await goToSnackCard(page);

    const concept = page.locator('.snack-card__concept');
    await expect(concept.locator('.rich-text__keyword')).toHaveCount(1);
    await expect(concept.locator('.rich-text__keyword').first()).toHaveAttribute(
      'data-term',
      'light reactions',
    );
    // No keyword control nested inside another keyword control.
    await expect(concept.locator('.rich-text__keyword .rich-text__keyword')).toHaveCount(0);
  });

  test('a term inside **bold** renders both <strong> and a keyword control', async ({ page }) => {
    await setupFixtures(page, BOLD_SNACK);
    await goToSnackCard(page);

    const concept = page.locator('.snack-card__concept');
    await expect(concept.locator('strong')).toHaveCount(1);
    await expect(concept.locator('strong .rich-text__keyword')).toHaveCount(1);
    await expect(concept.locator('strong .rich-text__keyword')).toHaveText('Photosynthesis');
  });

  test('regex-like terms (C++, (x), a.b) match literally without breaking rendering', async ({
    page,
  }) => {
    await setupFixtures(page, REGEX_SNACK);
    await goToSnackCard(page);

    await expect(keywordIn(page, '.snack-card__concept', 'C++')).toHaveCount(1);
    await expect(keywordIn(page, '.snack-card__concept', '(x)')).toHaveCount(1);
    await expect(keywordIn(page, '.snack-card__concept', 'a.b')).toHaveCount(1);
  });

  test('related chips are never highlighted', async ({ page }) => {
    await setupFixtures(page);
    await goToSnackCard(page);

    await expect(page.locator('.snack-card__related .rich-text__keyword')).toHaveCount(0);
    await expect(page.locator('.snack-card__related .tag').first()).toHaveText('Chlorophyll');
  });
});

// ─── US3: definition card ─────────────────────────────────────────────────────

test.describe('US3 — clicking/tapping a term opens a dismissible definition card', () => {
  test('click opens a card showing only the ≤3-word definition; outside-click closes it', async ({
    page,
  }) => {
    await setupFixtures(page);
    await goToSnackCard(page);

    await keywordIn(page, '.snack-card__concept', 'photosynthesis').first().click();
    const card = page.locator('.keyword-card');
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__term')).toHaveText('photosynthesis');
    await expect(card.locator('.keyword-card__definition')).toHaveText('light to sugar');
    expect(
      (await card.locator('.keyword-card__definition').innerText()).split(/\s+/).length,
    ).toBeLessThanOrEqual(3);

    // Outside pointerdown dismisses with no navigation.
    await page.locator('.page__title').click();
    await expect(card).toHaveCount(0);
    await expect(page).toHaveURL(/\/snacks$/);
  });

  test('the close control dismisses the card with no navigation', async ({ page }) => {
    await setupFixtures(page);
    await goToSnackCard(page);

    await keywordIn(page, '.snack-card__metaphor', 'solar kitchen').first().click();
    const card = page.locator('.keyword-card');
    await expect(card).toBeVisible();

    await card.locator('.keyword-card__close').click();
    await expect(card).toHaveCount(0);
    await expect(page).toHaveURL(/\/snacks$/);
  });

  test('Escape dismisses the card and returns focus to the term control', async ({ page }) => {
    await setupFixtures(page);
    await goToSnackCard(page);

    const term = keywordIn(page, '.snack-card__concept', 'photosynthesis').first();
    await term.click();
    await expect(page.locator('.keyword-card')).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(page.locator('.keyword-card')).toHaveCount(0);
    const focusedIsTerm = await page.evaluate(
      () =>
        document.activeElement?.classList.contains('rich-text__keyword') &&
        document.activeElement?.getAttribute('data-term') === 'photosynthesis',
    );
    expect(focusedIsTerm).toBe(true);
  });

  test('opening a different term closes the previous card and opens the new one', async ({
    page,
  }) => {
    await setupFixtures(page);
    await goToSnackCard(page);

    await keywordIn(page, '.snack-card__concept', 'photosynthesis').first().click();
    await expect(page.locator('.keyword-card')).toHaveCount(1);
    await expect(page.locator('.keyword-card__definition')).toHaveText('light to sugar');

    // The first card is `position: fixed` near the concept term; scroll the second term clear of it
    // so the pointer reaches the term (a real user would scroll to reach a covered line).
    const second = keywordIn(page, '.snack-card__metaphor', 'solar kitchen').first();
    await second.evaluate((el) => el.scrollIntoView({ block: 'center' }));
    await second.click();
    await expect(page.locator('.keyword-card')).toHaveCount(1);
    await expect(page.locator('.keyword-card__definition')).toHaveText('leaf factory');
  });

  test('keyboard: Tab to a term, Enter opens the card, Escape returns focus', async ({ page }) => {
    await setupFixtures(page);
    await goToSnackCard(page);

    expect(await tabUntilFocused(page, '.snack-card__concept .rich-text__keyword')).toBe(true);
    await page.keyboard.press('Enter');
    await expect(page.locator('.keyword-card')).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(page.locator('.keyword-card')).toHaveCount(0);
    expect(
      await page.evaluate(() => document.activeElement?.classList.contains('rich-text__keyword')),
    ).toBe(true);
  });

  test('desktop and mobile: the card is fully visible with no horizontal overflow', async ({
    page,
  }) => {
    for (const viewport of [
      { width: 1280, height: 800 },
      { width: 375, height: 720 },
    ]) {
      await page.setViewportSize(viewport);
      await setupFixtures(page);
      await goToSnackCard(page);

      await keywordIn(page, '.snack-card__concept', 'photosynthesis').first().click();
      const card = page.locator('.keyword-card');
      await expect(card).toBeVisible();

      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth > window.innerWidth + 1,
      );
      expect(overflow, `no horizontal overflow at ${viewport.width}px`).toBe(false);
    }
  });

  test('a term near the viewport bottom opens a card fully inside the viewport', async ({
    page,
  }) => {
    // A short viewport plus a trailing spacer lets the term scroll to the very bottom edge — the
    // case where the card would otherwise open below the fold. The card host is moved next to the
    // anchor so its unpositioned static position cannot scroll the anchor away on open.
    await page.setViewportSize({ width: 1280, height: 500 });
    await setupFixtures(page);
    await goToSnackCard(page);
    await page.evaluate(() => {
      const spacer = document.createElement('div');
      spacer.style.height = '800px';
      document.body.appendChild(spacer);
      const why = document.querySelector('.snack-card__why');
      const host = document.querySelector('app-keyword-definition-card');
      why?.parentElement?.insertBefore(host as Node, why.nextSibling);
    });

    const term = keywordIn(page, '.snack-card__why', 'light').first();
    await term.evaluate((el) => el.scrollIntoView({ block: 'end' }));
    await page.waitForTimeout(100);
    // Sanity: the anchor really sits at the bottom edge of the viewport.
    const anchorGap = await term.evaluate(
      (el) => window.innerHeight - el.getBoundingClientRect().bottom,
    );
    expect(anchorGap, 'anchor is within a card height of the viewport bottom').toBeLessThan(80);
    await term.evaluate((el) => (el as HTMLElement).click());

    const card = page.locator('.keyword-card');
    await expect(card).toBeVisible();

    const rect = await card.evaluate((el) => {
      const r = el.getBoundingClientRect();
      return { top: r.top, bottom: r.bottom, viewportHeight: window.innerHeight };
    });
    expect(rect.top, 'card top stays inside the viewport').toBeGreaterThanOrEqual(0);
    expect(rect.bottom, 'card bottom stays inside the viewport').toBeLessThanOrEqual(
      rect.viewportHeight,
    );
  });
});

// ─── US4: security ────────────────────────────────────────────────────────────

test.describe('US4 — untrusted keyword text is escaped', () => {
  test('keyword payloads execute nothing and inject no nodes; readable text preserved', async ({
    page,
  }) => {
    await setupFixtures(page, XSS_SNACK);
    await goToSnackCard(page);

    expect(
      await page.evaluate(() => (window as unknown as { __xss?: number }).__xss),
    ).toBeUndefined();
    for (const selector of ['script', 'img', 'iframe', 'style', 'a']) {
      await expect(page.locator(`.snack-card ${selector}`)).toHaveCount(0);
    }
    await expect(page.locator('.snack-card__concept')).toContainText('Safe concept text');
    await expect(page.locator('.snack-card__metaphor')).toContainText('Metaphor text');
  });

  test('the definition card shows escaped text and stays inert', async ({ page }) => {
    await setupFixtures(page, XSS_SNACK);
    await goToSnackCard(page);

    // Open the card for the script-payload term.
    await page.locator('.snack-card__concept .rich-text__keyword').first().click();
    const card = page.locator('.keyword-card');
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__definition')).toContainText('<img');

    expect(
      await page.evaluate(() => (window as unknown as { __xss?: number }).__xss),
    ).toBeUndefined();
    for (const selector of ['script', 'img', 'iframe', 'style', 'a']) {
      await expect(card.locator(selector)).toHaveCount(0);
    }
  });

  test('the results recap neutralizes the same keyword payloads (FR-025, SC-007)', async ({
    page,
  }) => {
    await setupFixtures(page, XSS_SNACK);
    await overrideSummaryWithRecap(page, XSS_SNACK);
    await goToSnackCard(page);
    await completeQuizToResults(page);

    expect(
      await page.evaluate(() => (window as unknown as { __xss?: number }).__xss),
    ).toBeUndefined();
    for (const selector of ['script', 'img', 'iframe', 'style', 'a']) {
      await expect(page.locator(`.recap-card ${selector}`)).toHaveCount(0);
    }
    await expect(page.locator('.recap-card__concept')).toContainText('Safe concept text');

    // The recap's definition card renders the untrusted definition as escaped, inert text.
    await page.locator('.recap-card__concept .rich-text__keyword').first().click();
    const card = page.locator('.keyword-card');
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__definition')).toContainText('<img');
    expect(
      await page.evaluate(() => (window as unknown as { __xss?: number }).__xss),
    ).toBeUndefined();
    for (const selector of ['script', 'img', 'iframe', 'style', 'a']) {
      await expect(card.locator(selector)).toHaveCount(0);
    }
  });
});

// ─── US5: graceful degradation ────────────────────────────────────────────────

test.describe('US5 — empty/absent keywords degrade gracefully', () => {
  test('keywords: [] renders with no highlight and no card affordance', async ({ page }) => {
    await setupFixtures(page, EMPTY_KEYWORDS_SNACK);
    await goToSnackCard(page);

    await expect(page.locator('.snack-card .rich-text__keyword')).toHaveCount(0);
    await expect(page.locator('.keyword-card')).toHaveCount(0);
    await expect(page.locator('.snack-card__concept')).toContainText('Plain concept');
    await expect(page.locator('.snack-card .error-banner')).toHaveCount(0);
  });

  test('a snack with no keywords field renders exactly as before', async ({ page }) => {
    await setupFixtures(page, ABSENT_KEYWORDS_SNACK);
    await goToSnackCard(page);

    await expect(page.locator('.snack-card .rich-text__keyword')).toHaveCount(0);
    await expect(page.locator('.snack-card__concept')).toContainText('without a keywords field');
    await expect(page.locator('.snack-card')).not.toContainText('**');
  });

  test('a very large keyword list remains stable and highlights without layout break', async ({
    page,
  }) => {
    await setupFixtures(page, LARGE_KEYWORDS_SNACK);
    await goToSnackCard(page);

    const concept = page.locator('.snack-card__concept');
    await expect(concept.locator('.rich-text__keyword')).toHaveCount(10);
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > window.innerWidth + 1,
    );
    expect(overflow).toBe(false);
  });
});

// ─── Cross-surface consistency ────────────────────────────────────────────────

test.describe('Cross-surface — the snack card and the results recap highlight consistently', () => {
  test('the same snack renders the same highlights on the card and the recap', async ({ page }) => {
    await setupFixtures(page, KEYWORD_SNACK);
    await overrideSummaryWithRecap(page, KEYWORD_SNACK);
    await goToSnackCard(page);

    await expect(keywordIn(page, '.snack-card__concept', 'photosynthesis')).toHaveCount(2);

    await completeQuizToResults(page);

    const recap = page.locator('.recap-card__concept');
    await expect(recap.locator('.rich-text__keyword[data-term="photosynthesis"]')).toHaveCount(2);
    await expect(recap.locator('.rich-text__keyword').first()).toHaveText('Photosynthesis');
  });

  test('the recap definition card opens and dismisses', async ({ page }) => {
    await setupFixtures(page, KEYWORD_SNACK);
    await overrideSummaryWithRecap(page, KEYWORD_SNACK);
    await goToSnackCard(page);
    await completeQuizToResults(page);

    await page.locator('.recap-card__concept .rich-text__keyword').first().click();
    const card = page.locator('.keyword-card');
    await expect(card).toBeVisible();
    await expect(card.locator('.keyword-card__definition')).toHaveText('light to sugar');

    await page.keyboard.press('Escape');
    await expect(card).toHaveCount(0);
  });
});
