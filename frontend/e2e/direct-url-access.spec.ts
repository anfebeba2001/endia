import { test, expect } from './helpers';

test.describe('P1 #11 — Direct URL access', () => {
  test('navigating to /quiz directly redirects away', async ({ mockPage: page }) => {
    await page.goto('/quiz');
    await page.waitForTimeout(2000);
    const url = page.url();
    // The app guards against missing state — should redirect away from /quiz
    expect(url).not.toContain('/quiz');
  });

  test('navigating to /results directly redirects away', async ({ mockPage: page }) => {
    await page.goto('/results');
    await page.waitForTimeout(2000);
    const url = page.url();
    expect(url).not.toContain('/results');
  });

  test('navigating to /subtopics without state renders empty subtopics page', async ({ mockPage: page }) => {
    // The subtopics page does NOT redirect — it shows empty state since AppState has no theme
    await page.goto('/subtopics');
    await page.waitForTimeout(2000);
    // The page renders but has no subtopics to show (empty grid)
    const cards = page.locator('.subtopic-card');
    await expect(cards).toHaveCount(0);
  });
});
