import { test, expect, angularSubmitHome, goToSnacks, goToQuiz } from './helpers';

test.describe('P2 — Progressive snack delivery (FR-002/004)', () => {
  test('renders snacks one at a time as they become ready', async ({ page }) => {
    // Mock theme + batch with a mix: item0 READY, item1 GENERATING (not ready yet).
    await page.route('**/api/themes/suggest', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          themeId: 'test-theme-001',
          subtopics: [
            { id: 'sub-001', name: 'Light reactions' },
            { id: 'sub-002', name: 'Calvin cycle' },
          ],
        }),
      });
    });
    await page.route('**/api/batches', async (route) => {
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({
          batchId: 'test-batch-001',
          themeId: 'test-theme-001',
          status: 'IN_PROGRESS',
          items: [
            { subtopicId: 'sub-001', snackStatus: 'PENDING', quizStatus: 'PENDING' },
            { subtopicId: 'sub-002', snackStatus: 'PENDING', quizStatus: 'PENDING' },
          ],
        }),
      });
    });
    let poll = 0;
    await page.route('**/api/batches/*', async (route) => {
      poll++;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          batchId: 'test-batch-001',
          themeId: 'test-theme-001',
          status: 'IN_PROGRESS',
          retrying: poll < 3,
          items: [
            {
              subtopicId: 'sub-001',
              subtopicName: 'Light reactions',
              snackStatus: 'READY',
              snack: {
                id: 'snack-0',
                subtopicId: 'sub-001',
                subtopicName: 'Light reactions',
                concept: 'Light energy is captured.',
                metaphor: 'Solar factory.',
                whyItMatters: 'Produces ATP and NADPH.',
                related: ['ATP'],
                keywords: [],
              },
              snackCause: null,
              quizStatus: 'READY',
              quizCause: null,
            },
            {
              subtopicId: 'sub-002',
              subtopicName: 'Calvin cycle',
              snackStatus: poll < 3 ? 'GENERATING' : 'READY',
              snack:
                poll < 3
                  ? null
                  : {
                      id: 'snack-1',
                      subtopicId: 'sub-002',
                      subtopicName: 'Calvin cycle',
                      concept: 'CO2 becomes sugar.',
                      metaphor: 'Assembly line.',
                      whyItMatters: 'Builds glucose.',
                      related: ['RuBP'],
                      keywords: [],
                    },
              snackCause: null,
              quizStatus: 'READY',
              quizCause: null,
            },
          ],
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

    // Only one snack card at a time (progressive).
    await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });
    await expect(page.locator('.snack-card')).toHaveCount(1);
    await expect(page.locator('.snack-card__chip')).toHaveText('Light reactions');

    // First poll reports retrying → spinner visible.
    await expect(page.locator('.retry-spinner')).toBeVisible();

    // Advance to the second snack once it becomes READY.
    await page.locator('.snack-card .primary-btn').click();
    await page
      .locator('.snack-card__chip')
      .filter({ hasText: 'Calvin cycle' })
      .waitFor({ state: 'visible', timeout: 8000 });
    await expect(page.locator('.snack-card__chip')).toHaveText('Calvin cycle');
  });

  test('Start Quiz uses prefetched questions without regeneration call or spinner (FR-003/SC-002)', async ({
    page,
  }) => {
    // Mock theme + batch whose items report quizStatus READY with prefetched questions.
    await page.route('**/api/themes/suggest', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          themeId: 'test-theme-001',
          subtopics: [
            { id: 'sub-001', name: 'Light reactions' },
            { id: 'sub-002', name: 'Calvin cycle' },
          ],
        }),
      });
    });
    await page.route('**/api/batches', async (route) => {
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({
          batchId: 'test-batch-001',
          themeId: 'test-theme-001',
          status: 'IN_PROGRESS',
          items: [
            { subtopicId: 'sub-001', snackStatus: 'PENDING', quizStatus: 'PENDING' },
            { subtopicId: 'sub-002', snackStatus: 'PENDING', quizStatus: 'PENDING' },
          ],
        }),
      });
    });
    await page.route('**/api/batches/*', async (route) => {
      await route.fulfill({
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
              snack: {
                id: 'snack-0',
                subtopicId: 'sub-001',
                subtopicName: 'Light reactions',
                concept: 'Light energy is captured.',
                metaphor: 'Solar factory.',
                whyItMatters: 'Produces ATP and NADPH.',
                related: ['ATP'],
                keywords: [],
              },
              snackCause: null,
              quizStatus: 'READY',
              quizCause: null,
              quizQuestions: [
                {
                  id: 'q-001',
                  type: 'MULTIPLE_CHOICE',
                  prompt: 'What is the primary role of chlorophyll?',
                  options: [
                    'Absorbing light',
                    'Storing glucose',
                    'Releasing oxygen',
                    'Transporting water',
                  ],
                  variant: null,
                  dragDrop: null,
                },
                {
                  id: 'q-002',
                  type: 'MULTIPLE_CHOICE',
                  prompt: 'Where do light reactions occur?',
                  options: [
                    'Thylakoid membrane',
                    'Stroma',
                    'Outer membrane',
                    'Intermembrane space',
                  ],
                  variant: null,
                  dragDrop: null,
                },
                {
                  id: 'q-003',
                  type: 'MULTIPLE_CHOICE',
                  prompt: 'What is the final electron acceptor?',
                  options: ['NADP+', 'Oxygen', 'CO2', 'ATP'],
                  variant: null,
                  dragDrop: null,
                },
                {
                  id: 'q-004',
                  type: 'MULTIPLE_CHOICE',
                  prompt: 'What is produced by the light reactions?',
                  options: ['ATP and NADPH', 'Glucose', 'CO2', 'RuBP'],
                  variant: null,
                  dragDrop: null,
                },
              ],
            },
          ],
        }),
      });
    });

    // Track whether the regeneration endpoint is ever hit.
    let generateCalled = false;
    await page.route('**/api/quiz/generate', async (route) => {
      generateCalled = true;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ subtopicId: 'sub-001', questions: [] }),
      });
    });

    await page.goto('/');
    await page.waitForTimeout(2000);
    await angularSubmitHome(page, 'Photosynthesis');
    await page.waitForURL('**/subtopics');
    await goToSnacks(page);
    await goToQuiz(page);

    // The quiz screen renders immediately: no loading spinner, question text visible.
    await expect(page.locator('.loading')).not.toBeVisible();
    await expect(page.locator('.question')).toHaveText('What is the primary role of chlorophyll?');
    expect(generateCalled).toBe(false);
  });
});
