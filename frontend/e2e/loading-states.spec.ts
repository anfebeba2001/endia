import { test, expect, angularSubmitHome } from './helpers';

test.describe('P1 #9 — Loading states', () => {
  test('home page shows loading indicator during API call', async ({ mockPage: page }) => {
    // Slow down the themes/suggest response to see loading
    await page.unroute('**/api/themes/suggest');
    await page.route('**/api/themes/suggest', async (route) => {
      await new Promise((r) => setTimeout(r, 500));
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
    });

    await page.goto('/');
    await page.waitForTimeout(2000);

    // Set value and submit
    await page.evaluate(() => {
      const ng = (window as any).ng;
      const el = document.querySelector('app-home');
      if (ng && el) {
        const comp = ng.getComponent(el);
        comp.themeControl.setValue('Photosynthesis');
        comp.onSubmit();
      }
    });

    // Button should show "Thinking…" during loading
    await expect(page.locator('button.search__button')).toHaveText('Thinking…');

    // Wait for navigation
    await page.waitForURL('**/subtopics');
  });

  test('snacks page shows loading text during generation', async ({ mockPage: page }) => {
    // Slow down snacks/generate response
    await page.unroute('**/api/snacks/generate');
    await page.route('**/api/snacks/generate', async (route) => {
      await new Promise((r) => setTimeout(r, 500));
      const body = JSON.parse(route.request().postData() || '{}');
      const snacks = (body.subtopicIds || []).map((id: string, i: number) => ({
        id: `snack-${i}`,
        subtopicId: id,
        subtopicName: `Subtopic ${i + 1}`,
        concept: `Concept ${i + 1}`,
        metaphor: `Metaphor ${i + 1}`,
        whyItMatters: `Why ${i + 1}`,
        related: ['tag1'],
      }));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ snacks }),
      });
    });

    await page.goto('/');
    await page.waitForTimeout(2000);
    await angularSubmitHome(page, 'Photosynthesis');
    await page.waitForURL('**/subtopics');
    await page.click('button.primary-btn');
    await page.waitForURL('**/snacks');

    // Should see loading text
    await expect(page.locator('.loading')).toBeVisible();
    await expect(page.locator('.loading')).toContainText(/warming|oven/i);
  });
});
