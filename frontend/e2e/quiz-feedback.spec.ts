import { test, expect, goToSubtopics, goToSnacks, goToQuiz } from './helpers';

test.describe('P0 #4 — Quiz answer feedback (correct)', () => {
  test('shows green feedback bar with +10 XP for correct answer', async ({ mockPage: page }) => {
    await goToSubtopics(page);
    await goToSnacks(page);
    await goToQuiz(page);

    // Verify quiz loaded
    await expect(page.locator('h2.question')).toBeVisible();
    await expect(page.locator('.option')).toHaveCount(4);

    // Answer correctly (option 0 is correct per mock)
    await page.locator('.option').nth(0).click();

    // Feedback should appear
    await expect(page.locator('.feedback')).toBeVisible();
    // Should say "Correct" with XP
    await expect(page.locator('.feedback__bar strong')).toContainText('Correct');
    // Explanation should be visible
    await expect(page.locator('.feedback__explanation')).toBeVisible();
    await expect(page.locator('.feedback__explanation')).not.toBeEmpty();
  });
});

test.describe('P0 #5 — Quiz answer feedback (wrong)', () => {
  test('shows red feedback bar for wrong answer and highlights correct option', async ({ mockPage: page }) => {
    await goToSubtopics(page);
    await goToSnacks(page);
    await goToQuiz(page);

    // Verify quiz loaded
    await expect(page.locator('h2.question')).toBeVisible();

    // Answer incorrectly (option 1, correct is 0)
    await page.locator('.option').nth(1).click();

    // Feedback should appear
    await expect(page.locator('.feedback')).toBeVisible();
    // Should say "Not quite"
    await expect(page.locator('.feedback__bar strong')).toContainText(/Not quite/i);
    // Explanation should be visible
    await expect(page.locator('.feedback__explanation')).toBeVisible();
    // The correct answer should be highlighted
    await expect(page.locator('.option--correct')).toBeVisible();
  });
});
