import { test, expect } from './helpers';

test.describe('P0 #3 — Subtopic deselection', () => {
  test('deselecting subtopics updates the count and only sends selected ones', async ({ mockPage: page }) => {
    // Track API calls via request event
    const capturedRequests: { url: string; body: any }[] = [];
    page.on('request', (req) => {
      if (req.url().includes('/api/snacks/generate')) {
        try {
          capturedRequests.push({
            url: req.url(),
            body: JSON.parse(req.postData() || '{}'),
          });
        } catch {
          // ignore parse errors
        }
      }
    });

    // 1. Go to subtopics page via Angular submit
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

    // 2. Verify initial state: 3 selected, button shows (3)
    const cards = page.locator('.subtopic-card');
    await expect(cards).toHaveCount(3);
    await expect(page.locator('button.primary-btn')).toHaveText(/Generate snacks \(3\)/);

    // 3. Deselect first subtopic
    await cards.nth(0).click();
    await expect(page.locator('button.primary-btn')).toHaveText(/Generate snacks \(2\)/);

    // 4. Deselect second subtopic
    await cards.nth(1).click();
    await expect(page.locator('button.primary-btn')).toHaveText(/Generate snacks \(1\)/);

    // 5. Re-select first subtopic
    await cards.nth(0).click();
    await expect(page.locator('button.primary-btn')).toHaveText(/Generate snacks \(2\)/);

    // 6. Click generate and verify API receives correct IDs
    await page.click('button.primary-btn');
    await page.waitForURL('**/snacks');

    // Wait for the request to be captured
    await page.waitForTimeout(500);

    // Verify the API was called with the correct subtopic IDs
    expect(capturedRequests.length).toBeGreaterThan(0);
    const lastRequest = capturedRequests[capturedRequests.length - 1];
    const ids = lastRequest.body.subtopicIds;
    expect(ids).toContain('sub-001');
    expect(ids).toContain('sub-003');
    expect(ids).not.toContain('sub-002');
    expect(ids).toHaveLength(2);
  });
});
