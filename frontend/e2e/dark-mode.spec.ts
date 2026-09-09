import { test, expect } from './helpers';

test.describe('P2 — Dark mode (FR-011..014)', () => {
  test('toggle switches theme and persists across reload', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(2000);

    const html = page.locator('html');
    // Default light
    await expect(html).not.toHaveAttribute('data-theme', 'dark');

    // Toggle to dark
    await page.click('button.theme-toggle');
    await expect(html).toHaveAttribute('data-theme', 'dark');
    await expect(page.locator('button.theme-toggle')).toContainText('Light');

    // Reload → theme persists
    await page.reload();
    await page.waitForTimeout(2000);
    await expect(html).toHaveAttribute('data-theme', 'dark');

    // Toggle back
    await page.click('button.theme-toggle');
    await expect(html).toHaveAttribute('data-theme', 'light');
  });
});
