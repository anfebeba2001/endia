import { test, expect, goToSubtopics, goToSnacks, goToQuiz } from './helpers';

test.describe('P2 #12 — Perfect score', () => {
  test('answering all questions correctly shows perfect score with trophy', async ({ mockPage: page }) => {
    // Override summary mock to return all correct
    await page.unroute('**/api/quiz/summary/*');
    await page.route('**/api/quiz/summary/*', async (route) => {
      const url = new URL(route.request().url());
      const subtopicId = url.pathname.split('/').pop();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          subtopicId,
          totalQuestions: 4,
          correctAnswers: 4,
          totalXp: 40,
          questions: [
            { questionId: 'q-001', prompt: 'Q1?', options: ['A', 'B', 'C', 'D'], chosenIndex: 0, correct: true, correctIndex: 0, explanation: 'Exp1' },
            { questionId: 'q-002', prompt: 'Q2?', options: ['A', 'B', 'C', 'D'], chosenIndex: 0, correct: true, correctIndex: 0, explanation: 'Exp2' },
            { questionId: 'q-003', prompt: 'Q3?', options: ['A', 'B', 'C', 'D'], chosenIndex: 0, correct: true, correctIndex: 0, explanation: 'Exp3' },
            { questionId: 'q-004', prompt: 'Q4?', options: ['A', 'B', 'C', 'D'], chosenIndex: 0, correct: true, correctIndex: 0, explanation: 'Exp4' },
          ],
        }),
      });
    });

    await goToSubtopics(page);
    await goToSnacks(page);
    await goToQuiz(page);

    // Answer all correctly (option 0 = correct per mock)
    for (let i = 0; i < 4; i++) {
      await expect(page.locator('h2.question')).toBeVisible();
      await page.locator('.option').nth(0).click();
      await expect(page.locator('.feedback')).toBeVisible();
      await page.click('.feedback .primary-btn');
    }

    // Results page
    await page.waitForURL('**/results');
    await page.waitForTimeout(500);
    await expect(page.locator('h1.results-header__title')).toHaveText('Perfect score!');
    // Perfect badge with bonus XP should be visible
    await expect(page.locator('.perfect-badge')).toBeVisible();
    await expect(page.locator('.perfect-badge')).toContainText('bonus XP');
  });
});
