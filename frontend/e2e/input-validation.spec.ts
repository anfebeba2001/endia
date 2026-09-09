import { test, expect } from './helpers';

test.describe('P0 #2 — Input validation', () => {
  test('shows error for short input and does NOT call API', async ({ mockPage: page }) => {
    let apiCalled = false;
    page.on('request', (req) => {
      if (req.url().includes('/api/themes/suggest')) {
        apiCalled = true;
      }
    });

    await page.goto('/');
    await page.waitForTimeout(2000);

    // Try to submit with invalid input via Angular debug API
    await page.evaluate(() => {
      const ng = (window as any).ng;
      const el = document.querySelector('app-home');
      if (ng && el) {
        const comp = ng.getComponent(el);
        comp.themeControl.setValue('a');
        comp.onSubmit();
      }
    });

    // Should stay on home page
    expect(page.url()).not.toContain('/subtopics');
    // Error banner should appear
    await expect(page.locator('.error-banner')).toBeVisible();
    await expect(page.locator('.error-banner')).toContainText(/at least 2 characters/i);
    // Button should NOT show "Thinking…"
    await expect(page.locator('button.search__button')).toHaveText('Suggest subtopics');
    // API should NOT have been called
    expect(apiCalled).toBe(false);
  });

  test('shows error for empty input', async ({ mockPage: page }) => {
    await page.goto('/');
    await page.waitForTimeout(2000);

    // Try to submit with empty input
    await page.evaluate(() => {
      const ng = (window as any).ng;
      const el = document.querySelector('app-home');
      if (ng && el) {
        const comp = ng.getComponent(el);
        comp.themeControl.setValue('');
        comp.onSubmit();
      }
    });

    // Should stay on home page
    expect(page.url()).not.toContain('/subtopics');
    // Error banner should appear
    await expect(page.locator('.error-banner')).toBeVisible();
  });
});
