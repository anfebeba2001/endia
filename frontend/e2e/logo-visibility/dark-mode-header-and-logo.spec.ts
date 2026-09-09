import { test, expect } from './fixtures';
import { readHeaderStyles } from './styles';

// spec: specs/001-batch-mvp-enhancements/e2e-test-plan.md / 1.2

test.describe('Logo visibility across theme states', () => {
  test('dark mode shows dark header + near-white logo', async ({ page }) => {
    // 1. Load the app, then press the theme toggle to switch to dark.
    //    - expect: html has data-theme="dark"
    //    - expect: header bg is the dark surface
    //    - expect: logo color is near-white
    //    - expect: toggle aria-label flips
    await page.getByRole('button', { name: 'Switch to dark mode' }).click();
    await expect(page.getByRole('button', { name: 'Switch to light mode' })).toBeVisible();
    const s = await readHeaderStyles(page);
    expect(s.htmlTheme).toBe('dark');
    expect(s.headerBg).toBe('rgba(33, 29, 24, 0.88)');
    expect(s.logoColor).toBe('rgb(242, 239, 231)');
    expect(s.toggleAria).toBe('Switch to light mode');
  });
});
