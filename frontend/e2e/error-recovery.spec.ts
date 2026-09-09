import { test, expect } from './helpers';

test.describe('P1 #10 — Error recovery', () => {
  test('API 500 error shows banner, then retry works after fix', async ({ mockPage: page }) => {
    let callCount = 0;
    await page.unroute('**/api/themes/suggest');
    await page.route('**/api/themes/suggest', async (route) => {
      callCount++;
      if (callCount === 1) {
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'Internal server error' }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            themeId: 'test-theme-001',
            subtopics: [
              { id: 'sub-001', name: 'Light reactions' },
              { id: 'sub-002', name: 'Calvin cycle' },
              { id: 'sub-003', name: 'Chloroplast structure' },
            ],
          }),
        });
      }
    });

    await page.goto('/');
    await page.waitForTimeout(2000);

    // Submit with error
    await page.evaluate(() => {
      const ng = (window as any).ng;
      const el = document.querySelector('app-home');
      if (ng && el) {
        const comp = ng.getComponent(el);
        comp.themeControl.setValue('Photosynthesis');
        comp.onSubmit();
      }
    });

    // Error banner should appear
    await expect(page.locator('.error-banner')).toBeVisible();
    await expect(page.locator('.error-banner')).toContainText(/error|fail|server/i);

    // Button should be re-enabled
    await expect(page.locator('button.search__button')).toHaveText('Suggest subtopics');

    // Retry
    await page.evaluate(() => {
      const ng = (window as any).ng;
      const el = document.querySelector('app-home');
      if (ng && el) {
        const comp = ng.getComponent(el);
        comp.themeControl.setValue('Photosynthesis');
        comp.onSubmit();
      }
    });

    // Should succeed this time
    await page.waitForURL('**/subtopics');
    await expect(page.locator('h1.page__title')).toHaveText('Pick what to study');
  });
});
