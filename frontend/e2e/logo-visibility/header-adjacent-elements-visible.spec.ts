import { test, expect } from './fixtures';
import { readHeaderStyles } from './styles';

// spec: specs/001-batch-mvp-enhancements/e2e-test-plan.md / 1.4

test.describe('Logo visibility across theme states', () => {
  test('adjacent header elements stay visible in light and dark', async ({ page }) => {
    // 1. Load the app in light mode.
    //    - expect: theme toggle visible + labelled Dark
    //    - expect: brand bite dot rendered
    await expect(page.getByRole('button', { name: 'Switch to dark mode' })).toBeVisible();
    let s = await readHeaderStyles(page);
    expect(s.toggleText).toContain('Dark');
    expect(s.dotRendered).toBe(true);

    // 2. Toggle to dark mode.
    //    - expect: toggle flips to Light
    //    - expect: brand dot still rendered
    await page.getByRole('button', { name: 'Switch to dark mode' }).click();
    await expect(page.getByRole('button', { name: 'Switch to light mode' })).toBeVisible();
    s = await readHeaderStyles(page);
    expect(s.toggleText).toContain('Light');
    expect(s.dotRendered).toBe(true);
  });
});
