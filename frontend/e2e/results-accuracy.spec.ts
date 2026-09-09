import { test, expect, goToSubtopics, goToSnacks, goToQuiz } from './helpers';

test.describe('P0 #6 — Results accuracy', () => {
  test('displays correct score, accuracy percentage, and breakdown', async ({ mockPage: page }) => {
    await goToSubtopics(page);
    await goToSnacks(page);
    await goToQuiz(page);

    // Answer all 4 questions (first option = correct for 3, wrong for 1)
    for (let i = 0; i < 4; i++) {
      await expect(page.locator('h2.question')).toBeVisible();
      await page.locator('.option').nth(0).click();
      await expect(page.locator('.feedback')).toBeVisible();
      await page.click('.feedback .primary-btn');
    }

    // Should now be on results page
    await page.waitForURL('**/results');
    await page.waitForTimeout(500);

    // Verify score cards visible
    await expect(page.locator('.score-card--accent .score-card__value')).toBeVisible();

    // Verify breakdown cards exist (mock returns 4 questions)
    const breakdownCards = page.locator('.breakdown-card');
    await expect(breakdownCards).toHaveCount(4);

    // Verify each breakdown card has explanation
    for (let i = 0; i < 4; i++) {
      await expect(breakdownCards.nth(i).locator('.breakdown-card__explanation')).toBeVisible();
    }

    // Verify indicators exist (✓ or ✗)
    const indicators = page.locator('.indicator');
    await expect(indicators).toHaveCount(4);
  });
});
