import { test, expect } from './helpers';

test.describe('P0 #1 — Full happy path', () => {
  test('completes the entire learning loop without errors', async ({ mockPage: page }) => {
    // 1. Home: verify landing page
    await page.goto('/');
    await page.waitForTimeout(2000);
    await expect(page.locator('h1.hero__title')).toHaveText('Learn anything, bite by bite.');
    await expect(page.locator('button.search__button')).toHaveText('Suggest subtopics');

    // 2. Home → Subtopics: type theme and submit via Angular debug API
    await page.evaluate(() => {
      const ng = (window as any).ng;
      const el = document.querySelector('app-home');
      if (ng && el) {
        const comp = ng.getComponent(el);
        comp.themeControl.setValue('Photosynthesis');
        comp.onSubmit();
      }
    });
    await page.waitForURL('**/subtopics');

    // 3. Subtopics page
    await expect(page.locator('h1.page__title')).toHaveText('Pick what to study');
    const cards = page.locator('.subtopic-card');
    await expect(cards).toHaveCount(3);
    // All pre-selected (checkmark visible)
    const checks = page.locator('.subtopic-card__check');
    await expect(checks).toHaveCount(3);
    await expect(page.locator('button.primary-btn')).toHaveText(/Generate snacks \(3\)/);

    // 4. Subtopics → Snacks
    await page.click('button.primary-btn');
    await page.waitForURL('**/snacks');

    // 5. Snacks page — progressive one-at-a-time delivery (FR-002)
    const snackCard = page.locator('.snack-card');
    await snackCard.first().waitFor({ state: 'visible', timeout: 8000 });
    await expect(page.locator('h1.page__title')).toHaveText('Your Snacks');
    await expect(snackCard).toHaveCount(1);
    await expect(snackCard.locator('.snack-card__concept')).toBeVisible();
    await expect(snackCard.locator('.snack-card__metaphor')).toBeVisible();
    await expect(snackCard.locator('.snack-card__why')).toBeVisible();
    await expect(snackCard.locator('.quiz-btn')).toHaveText('Start Quiz');

    // 6. Snacks → Quiz
    await page.click('.quiz-btn');
    await page.waitForURL('**/quiz');
    await page.waitForTimeout(500);

    // 7. Quiz page — answer all 4 questions
    await expect(page.locator('h2.question')).toBeVisible();
    await expect(page.locator('.option')).toHaveCount(4);

    for (let i = 0; i < 4; i++) {
      await expect(page.locator('h2.question')).toBeVisible();
      await page.locator('.option').nth(0).click();
      await expect(page.locator('.feedback')).toBeVisible();
      if (i === 3) {
        await expect(page.locator('.feedback .primary-btn')).toHaveText('See results');
      }
      await page.click('.feedback .primary-btn');
    }

    // 8. Results page
    await page.waitForURL('**/results');
    await page.waitForTimeout(500);
    await expect(page.locator('h1.results-header__title')).toBeVisible();
    await expect(page.locator('.score-card--accent .score-card__value')).toBeVisible();
    const breakdownCards = page.locator('.breakdown-card');
    await expect(breakdownCards).toHaveCount(4);
  });
});
