import { test, expect } from './helpers';

test.describe('P2 — Drag-drop question + results recap (FR-005..010)', () => {
  test('completes a drag-drop question and shows the snack recap', async ({ page }) => {
    const THEME_ID = 'test-theme-001';
    const SUB_TOPIC = { id: 'sub-001', name: 'Light reactions' };

    await page.route('**/api/themes/suggest', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ themeId: THEME_ID, subtopics: [SUB_TOPIC] }),
      });
    });
    await page.route('**/api/batches', async (route) => {
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({
          batchId: 'test-batch-001',
          themeId: THEME_ID,
          status: 'IN_PROGRESS',
          items: [{ subtopicId: SUB_TOPIC.id, snackStatus: 'PENDING', quizStatus: 'PENDING' }],
        }),
      });
    });
    await page.route('**/api/batches/*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          batchId: 'test-batch-001',
          themeId: THEME_ID,
          status: 'COMPLETE',
          retrying: false,
          items: [{
            subtopicId: SUB_TOPIC.id,
            subtopicName: SUB_TOPIC.name,
            snackStatus: 'READY',
            snack: {
              id: 'snack-0', subtopicId: SUB_TOPIC.id, subtopicName: SUB_TOPIC.name,
              concept: 'Collects photons.', metaphor: 'Food for light.', whyItMatters: 'Power source.', related: ['ATP'],
            },
            snackCause: null,
            quizStatus: 'READY',
            quizCause: null,
          }],
        }),
      });
    });
    // One DRAG_DROP sequencing question.
    await page.route('**/api/quiz/generate', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          subtopicId: SUB_TOPIC.id,
          variant: 'SEQUENCING',
          questions: [{
            id: 'q-dd', type: 'DRAG_DROP',
            prompt: 'Order the light reactions.',
            options: [],
            variant: 'SEQUENCING',
            dragDrop: { items: ['Photon', 'Water split', 'Electron flow', 'NADPH'] },
          }],
        }),
      });
    });
    await page.route('**/api/quiz/answer', async (route) => {
      const body = JSON.parse(route.request().postData() || '{}');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          questionId: body.questionId,
          type: 'DRAG_DROP',
          correct: true,
          correctIndex: null,
          correctArrangement: body.arrangement || [],
          explanation: 'Photons drive the chain.',
          xpEarned: 10,
        }),
      });
    });
    await page.route('**/api/quiz/summary/*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          subtopicId: SUB_TOPIC.id,
          totalQuestions: 1,
          correctAnswers: 1,
          totalXp: 10,
          questions: [{
            questionId: 'q-dd', type: 'DRAG_DROP', prompt: 'Order the light reactions.',
            options: [], chosenIndex: null, correct: true, correctIndex: null,
            variant: 'SEQUENCING',
            correctArrangement: ['Photon', 'Water split', 'Electron flow', 'NADPH'],
            submittedArrangement: ['Photon', 'Water split', 'Electron flow', 'NADPH'],
            explanation: 'Photons drive the chain.',
          }],
          recap: {
            subtopicId: SUB_TOPIC.id,
            subtopicName: SUB_TOPIC.name,
            snack: {
              id: 'snack-0', subtopicId: SUB_TOPIC.id, subtopicName: SUB_TOPIC.name,
              concept: 'Collects photons.', metaphor: 'Food for light.', whyItMatters: 'Power source.', related: ['ATP'],
            },
          },
        }),
      });
    });

    await page.goto('/');
    await page.waitForTimeout(2000);
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
    await page.click('button.primary-btn');
    await page.waitForURL('**/snacks');
    await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });
    await page.click('.quiz-btn');
    await page.waitForURL('**/quiz');

    // Drag-drop component rendered.
    await expect(page.locator('app-drag-drop-question')).toBeVisible();
    await expect(page.locator('.dd-item')).toHaveCount(4);
    await page.click('button:has-text("Submit arrangement")');
    await expect(page.locator('.feedback')).toBeVisible();
    await page.click('.feedback .primary-btn');

    await page.waitForURL('**/results');
    // Recap card labeled with the subtopic name.
    await expect(page.locator('.recap-card')).toBeVisible();
    await expect(page.locator('.recap-card__chip')).toHaveText(SUB_TOPIC.name);
    await expect(page.locator('.recap-card__concept')).toBeVisible();
  });
});
