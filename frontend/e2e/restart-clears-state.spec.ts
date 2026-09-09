import { test, expect, goToSubtopics, goToSnacks, goToQuiz } from './helpers';

test.describe('P0 #7 — Restart clears state', () => {
  test('clicking "Start a new theme" resets to fresh home page', async ({ mockPage: page }) => {
    await goToSubtopics(page);
    await goToSnacks(page);
    await goToQuiz(page);

    // Answer all questions
    for (let i = 0; i < 4; i++) {
      await expect(page.locator('h2.question')).toBeVisible();
      await page.locator('.option').nth(0).click();
      await expect(page.locator('.feedback')).toBeVisible();
      await page.click('.feedback .primary-btn');
    }

    // On results page
    await page.waitForURL('**/results');
    await page.waitForTimeout(500);
    await expect(page.locator('h1.results-header__title')).toBeVisible();

    // Click "Start a new theme"
    await page.click('.results-footer .primary-btn');
    await page.waitForURL('/');

    // Verify we're back on home page with fresh state
    await expect(page.locator('h1.hero__title')).toHaveText('Learn anything, bite by bite.');
  });
});
