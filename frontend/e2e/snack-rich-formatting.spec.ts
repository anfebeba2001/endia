import { test, expect, type Page } from '@playwright/test';
import { setupAllMocks, angularSubmitHome } from './helpers';

/**
 * Feature 012 — snack rich formatting end to end.
 *
 * Covers:
 *  - US2 (FR-010..FR-013): bold → <strong>, bullets → <ul><li>, blank lines → separate <p>,
 *    `related` stays plain, on the snack card and the results recap.
 *  - US3 (FR-015..FR-019): `<script>`/`onerror`/`javascript:`/`<iframe>`/`<style>` payloads are
 *    neutralized (no execution, no injected nodes) while readable text is preserved.
 *  - US5 (FR-014): no-markup text renders cleanly with no empty <p>/<ul> and no mangling.
 *
 * Route mocks are installed per test. `setupAllMocks` provides the base flow; the batch and quiz
 * summary routes are overridden afterwards (Playwright runs the most recently registered matching
 * handler first) so the snack text can carry markup or payloads.
 */

const RICH_SNACK = {
  id: 'snack-rich-1',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept:
    '**Photosynthesis** is how plants convert light into chemical energy.\n\n' +
    'It happens in two stages:\n' +
    '- **Light reactions** capture energy.\n' +
    '- **Calvin cycle** fixes carbon.',
  metaphor: 'Think of a leaf as a **solar-powered kitchen**.',
  whyItMatters: 'A common pitfall: plants build themselves from **air and light**.',
  related: ['Chlorophyll', 'Glucose'],
  keywords: [],
};

const PLAIN_SNACK = {
  id: 'snack-plain-1',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: 'Light reactions convert light. A stray asterisk like 5 * 3 = 15 is not emphasis.',
  metaphor: 'An unclosed **important marker stays literal and does not swallow the rest.',
  whyItMatters: 'Plain text only, with no formatting markup at all.',
  related: ['Plain', 'Labels'],
  keywords: [],
};

const VARIANT_SNACK = {
  id: 'snack-variant-1',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept:
    'First paragraph with a single line.\nSecond line of the same paragraph.\n\n' +
    'A star bullet list:\n* alpha item\n* beta item\n\n' +
    'A round bullet list:\n• gamma item\n• delta item',
  metaphor: 'Metaphor **emphasized**.',
  whyItMatters: 'Why text.',
  related: ['One', 'Two'],
  keywords: [],
};

const XSS_SNACK = {
  id: 'snack-xss-1',
  subtopicId: 'sub-001',
  subtopicName: 'Light reactions',
  concept: '<script>window.__xss=1</script>Safe concept text',
  metaphor: '<img src=x onerror="window.__xss=1">Metaphor text',
  whyItMatters:
    '<a href="javascript:window.__xss=1">link</a> and <iframe></iframe> and <style>body{color:red}</style>',
  related: ['Plain', 'Labels'],
  keywords: [],
};

/** Override the batch status route with a single READY snack carrying the given fields. */
async function overrideBatchWithSnack(page: Page, snack: typeof RICH_SNACK): Promise<void> {
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

/** Install the base mocks plus a markup/payload-bearing snack batch. */
async function setupRichFixtures(page: Page, snack = RICH_SNACK): Promise<void> {
  await setupAllMocks(page);
  await overrideBatchWithSnack(page, snack);
}

/** Home → subtopics → snacks; returns once the snack card is visible. */
async function goToSnackCard(page: Page): Promise<void> {
  await page.goto('/');
  await page.waitForTimeout(2000);
  await angularSubmitHome(page, 'Photosynthesis');
  await page.waitForURL('**/subtopics');
  await page.click('button.primary-btn');
  await page.waitForURL('**/snacks');
  await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });
}

/** Override the quiz summary route with a recap snack carrying the given fields. */
async function overrideSummaryWithRecap(page: Page, snack: typeof RICH_SNACK): Promise<void> {
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
        recap: {
          subtopicId: 'sub-001',
          subtopicName: 'Light reactions',
          snack,
        },
      }),
    }),
  );
}

/** From the snack card, complete the quiz and land on /results. */
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

// ─── US2: rich rendering on the snack card ────────────────────────────────────

test.describe('US2 — snack card renders bold, bullets and paragraphs', () => {
  test('bold becomes <strong> and no literal ** is shown', async ({ page }) => {
    await setupRichFixtures(page);
    await goToSnackCard(page);

    const concept = page.locator('.snack-card__concept');
    await expect(concept.locator('strong')).toHaveText([
      'Photosynthesis',
      'Light reactions',
      'Calvin cycle',
    ]);
    await expect(page.locator('.snack-card')).not.toContainText('**');
  });

  test('bullet lines become list items, not leading dashes', async ({ page }) => {
    await setupRichFixtures(page);
    await goToSnackCard(page);

    const list = page.locator('.snack-card__concept ul');
    await expect(list).toHaveCount(1);
    await expect(list.locator('li')).toHaveCount(2);
    await expect(list.locator('li').first()).not.toContainText('- ');
  });

  test('blank lines become separate paragraphs', async ({ page }) => {
    await setupRichFixtures(page);
    await goToSnackCard(page);

    const concept = page.locator('.snack-card__concept');
    await expect(concept.locator('p')).toHaveCount(2);
    // The blank line before the bullet block splits the intro line into its own paragraph,
    // and it must not be merged into the first paragraph.
    await expect(concept.locator('p').nth(1)).toHaveText('It happens in two stages:');
    await expect(concept.locator('p').first()).not.toContainText('It happens in two stages');
  });

  test('single newline becomes a <br>, not a new paragraph', async ({ page }) => {
    await setupRichFixtures(page, VARIANT_SNACK);
    await goToSnackCard(page);

    const first = page.locator('.snack-card__concept p').first();
    await expect(first.locator('br')).toHaveCount(1);
    await expect(first).toContainText('First paragraph with a single line.');
    await expect(first).toContainText('Second line of the same paragraph.');
  });

  test('* and • bullet markers become list items, not literal markers', async ({ page }) => {
    await setupRichFixtures(page, VARIANT_SNACK);
    await goToSnackCard(page);

    const concept = page.locator('.snack-card__concept');
    await expect(concept.locator('ul')).toHaveCount(2);
    await expect(concept.locator('li')).toHaveText([
      'alpha item',
      'beta item',
      'gamma item',
      'delta item',
    ]);
    await expect(concept).not.toContainText('* alpha');
    await expect(concept).not.toContainText('• gamma');
  });

  test('related stays plain labels with no markup interpretation', async ({ page }) => {
    await setupRichFixtures(page);
    await goToSnackCard(page);

    await expect(page.locator('.snack-card__related .tag')).toHaveCount(2);
    await expect(page.locator('.snack-card__related .tag').first()).toHaveText('Chlorophyll');
    await expect(page.locator('.snack-card__related strong')).toHaveCount(0);
  });
});

// ─── US2: rich rendering in the results recap ────────────────────────────────

test.describe('US2 — results recap renders rich formatting', () => {
  test('recap snack renders bold, bullets and paragraphs', async ({ page }) => {
    await setupRichFixtures(page);
    await overrideSummaryWithRecap(page, RICH_SNACK);
    await goToSnackCard(page);
    await completeQuizToResults(page);

    const recap = page.locator('.recap-card__concept');
    await expect(recap.locator('strong').first()).toHaveText('Photosynthesis');
    await expect(recap.locator('ul li')).toHaveCount(2);
    await expect(recap.locator('p')).toHaveCount(2);
    await expect(page.locator('.recap-card')).not.toContainText('**');
  });
});

// ─── US3: sanitization on the snack card and the recap ───────────────────────

test.describe('US3 — untrusted markup is neutralized', () => {
  test('snack card executes nothing and injects no nodes, readable text preserved', async ({
    page,
  }) => {
    await setupRichFixtures(page, XSS_SNACK);
    await goToSnackCard(page);

    // No script/handler executed.
    expect(
      await page.evaluate(() => (window as unknown as { __xss?: number }).__xss),
    ).toBeUndefined();

    // No injected elements from snack text.
    for (const selector of ['script', 'img', 'iframe', 'style', 'a']) {
      await expect(page.locator(`.snack-card ${selector}`)).toHaveCount(0);
    }

    // Readable text is preserved.
    await expect(page.locator('.snack-card__concept')).toContainText('Safe concept text');
    await expect(page.locator('.snack-card__metaphor')).toContainText('Metaphor text');
  });

  test('results recap neutralizes the same payloads', async ({ page }) => {
    await setupRichFixtures(page, XSS_SNACK);
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
  });
});

// ─── US5: no-markup / legacy text renders cleanly ────────────────────────────

test.describe('US5 — no-markup text is not mangled', () => {
  test('plain field renders clean text with no empty <p>/<ul> or stray markers', async ({
    page,
  }) => {
    await setupRichFixtures(page, PLAIN_SNACK);
    await goToSnackCard(page);

    const concept = page.locator('.snack-card__concept');
    await expect(concept.locator('p')).toHaveCount(1);
    await expect(concept.locator('ul')).toHaveCount(0);
    await expect(concept.locator('strong')).toHaveCount(0);
    await expect(concept).toContainText('5 * 3 = 15');

    // An unclosed ** stays literal and does not swallow the rest of the field.
    const metaphor = page.locator('.snack-card__metaphor');
    await expect(metaphor).toContainText('**important marker stays literal');
    await expect(metaphor).toContainText('does not swallow the rest');
    await expect(metaphor.locator('strong')).toHaveCount(0);
  });
});
