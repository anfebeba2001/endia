import { type Page } from '@playwright/test';
import { test, expect, angularSubmitHome, setupQuizSummaryMock } from './helpers';

/**
 * US1 — Return to the snacks from the quiz results (FR-001..FR-006, FR-018, SC-001..SC-003, SC-009).
 *
 * Drives the full flow to `/results`, then exercises "Back to snacks" in both completion states
 * ("Quiz complete" and "Perfect score!") and asserts no new batch is created and the quizzed
 * subtopic's snack is active on return.
 */

/** Home → subtopics → snacks; returns once the first snack card is rendered. */
async function goToSnacksFromHome(page: Page): Promise<void> {
  await page.goto('/');
  await page.waitForTimeout(2000);
  await angularSubmitHome(page, 'Photosynthesis');
  await page.waitForURL('**/subtopics');
  await page.click('button.primary-btn');
  await page.waitForURL('**/snacks');
  await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });
}

/** Answer all four quiz questions and land on `/results`. */
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
  await expect(page.locator('h1.results-header__title')).toBeVisible();
}

test.describe('US1 — Return to snacks from results', () => {
  test('Quiz complete: renders Back to snacks and returns with no new batch', async ({
    mockPage: page,
  }) => {
    let batchPosts = 0;
    page.on('request', (req) => {
      if (req.method() === 'POST' && req.url().includes('/api/batches')) {
        batchPosts += 1;
      }
    });

    await goToSnacksFromHome(page);
    await completeQuizToResults(page);

    const back = page.locator('.results-footer [data-testid="back-to-snacks"]');
    await expect(back).toBeVisible();
    await expect(back).toHaveText('Back to snacks');
    // The restart action remains uniquely selectable (FR-006/FR-023).
    await expect(page.locator('.results-footer .primary-btn')).toHaveText('Start a new theme');

    const before = batchPosts;
    await back.click();
    await page.waitForURL('**/snacks');
    await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });

    // FR-002/SC-002: no new batch on return.
    expect(batchPosts).toBe(before);
    // FR-004: existing snacks render with no creation/loading/empty state.
    await expect(page.locator('.loading')).toHaveCount(0);
    await expect(page.locator('.empty-state')).toHaveCount(0);
    await expect(page.locator('.snack-index__item')).toHaveCount(3);
  });

  test('Perfect score: renders Back to snacks in the footer', async ({ mockPage: page }) => {
    await setupQuizSummaryMock(page, { correctAnswers: 4, totalXp: 40 });

    await goToSnacksFromHome(page);
    await completeQuizToResults(page);

    await expect(page.locator('h1.results-header__title')).toHaveText('Perfect score!');
    await expect(page.locator('.results-footer [data-testid="back-to-snacks"]')).toBeVisible();
    await expect(page.locator('.results-footer .primary-btn')).toBeVisible();
  });

  test('returns to the quizzed subtopic snack, not always the first', async ({
    mockPage: page,
  }) => {
    await goToSnacksFromHome(page);

    // Quiz the second snack ("Calvin cycle") instead of the first.
    await page.locator('.snack-index__item').nth(1).click();
    await expect(page.locator('.snack-card__chip')).toHaveText('Calvin cycle');

    await completeQuizToResults(page);
    await page.locator('.results-footer [data-testid="back-to-snacks"]').click();
    await page.waitForURL('**/snacks');
    await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });

    // FR-003/SC-003: the quizzed subtopic's snack is active.
    await expect(page.locator('.snack-card__chip')).toHaveText('Calvin cycle');
    const active = page.locator('.snack-index__item[aria-current="true"]');
    await expect(active).toHaveCount(1);
    await expect(active).toHaveText('Calvin cycle');
  });

  test('Start a new theme still resets state and lands on home', async ({ mockPage: page }) => {
    await goToSnacksFromHome(page);
    await completeQuizToResults(page);

    await page.click('.results-footer .primary-btn');
    await page.waitForURL('/');
    await expect(page.locator('h1.hero__title')).toHaveText('Learn anything, bite by bite.');
  });
});
