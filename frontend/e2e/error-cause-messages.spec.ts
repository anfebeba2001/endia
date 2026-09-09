import { test, expect } from './helpers';

test.describe('P2 — Error cause messages (FR-015)', () => {
  test('shows the specific friendly message for a QUOTA cause', async ({ page }) => {
    const THEME_ID = 'test-theme-001';
    await page.route('**/api/themes/suggest', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          themeId: THEME_ID,
          subtopics: [{ id: 'sub-001', name: 'Light reactions' }],
        }),
      });
    });
    // Batch create returns a QUOTA cause (429) → ErrorService renders cause-specific text.
    await page.route('**/api/batches', async (route) => {
      await route.fulfill({
        status: 429,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'QUOTA',
          message: 'The AI service quota or rate limit was reached. Try again shortly.',
          field: null,
          cause: 'QUOTA',
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

    await page.locator('.error-banner').waitFor({ state: 'visible', timeout: 5000 });
    await expect(page.locator('.error-banner')).toContainText(/quota or rate limit/i);
  });
});
