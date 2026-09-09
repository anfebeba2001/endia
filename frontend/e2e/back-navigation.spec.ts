import { test, expect, angularSubmitHome } from './helpers';

test.describe('P1 #8 — Back navigation', () => {
  test('back button on subtopics returns to home', async ({ mockPage: page }) => {
    await page.goto('/');
    await page.waitForTimeout(2000);
    await angularSubmitHome(page, 'Photosynthesis');
    await page.waitForURL('**/subtopics');

    // Click back
    await page.click('button.link-btn');
    await page.waitForURL('/');
    await expect(page.locator('h1.hero__title')).toHaveText('Learn anything, bite by bite.');
  });

  test('back button on snacks returns to subtopics', async ({ mockPage: page }) => {
    await page.goto('/');
    await page.waitForTimeout(2000);
    await angularSubmitHome(page, 'Photosynthesis');
    await page.waitForURL('**/subtopics');
    await page.click('button.primary-btn');
    await page.waitForURL('**/snacks');
    await page.waitForTimeout(500);

    // Click back
    await page.click('button.link-btn');
    await page.waitForURL('**/subtopics');
    await expect(page.locator('h1.page__title')).toHaveText('Pick what to study');
  });
});
