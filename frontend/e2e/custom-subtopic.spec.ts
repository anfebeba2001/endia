import { test, expect } from './helpers';

test.describe('Task 2 — Add custom focus area (regression)', () => {
  test('adding a custom focus area succeeds without INTERNAL_ERROR', async ({ page }) => {
    const THEME_ID = 'test-theme-001';

    // Mock the theme → subtopics suggestion endpoint.
    await page.route('**/api/themes/suggest', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          themeId: THEME_ID,
          subtopics: [
            { id: 'sub-001', name: 'Light reactions' },
            { id: 'sub-002', name: 'Calvin cycle' },
          ],
        }),
      });
    });

    // Mock the add-subtopic POST so it returns a normal success payload with
    // added[] and duplicates[] (the happy path for FR-019..021).
    await page.route('**/api/themes/*/subtopics', async (route) => {
      if (route.request().method() !== 'POST') {
        await route.continue();
        return;
      }
      const body = JSON.parse(route.request().postData() || '{}');
      const names: string[] = body.names || [];
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          themeId: THEME_ID,
          added: names.map((n) => ({
            id: `custom-${n.toLowerCase().replace(/\s+/g, '-')}`,
            name: n,
            source: 'USER_PROPOSED',
          })),
          duplicates: [],
        }),
      });
    });

    // Navigate to the Subtopics page via the Angular home submit path.
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

    // Fill the custom-name textbox and click Add.
    await page.locator('.add-subtopic__input').fill('C4 photosynthesis');
    await page.getByRole('button', { name: 'Add' }).click();

    // Success: the new subtopic appears in the grid and a success feedback shows.
    await expect(
      page.locator('.subtopic-grid .subtopic-card').filter({ hasText: 'C4 photosynthesis' }),
    ).toHaveCount(1);
    await expect(page.locator('.add-subtopic__feedback')).toContainText('Added "C4 photosynthesis"');

    // Regression guard: the generic INTERNAL_ERROR must NOT be surfaced anywhere.
    await expect(page.locator('.add-subtopic__feedback')).not.toContainText('An unexpected error occurred');
    await expect(page.locator('body')).not.toContainText('An unexpected error occurred');
  });
});
