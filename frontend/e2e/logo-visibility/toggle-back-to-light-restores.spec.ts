import { test, expect } from './fixtures';
import { readHeaderStyles } from './styles';

// spec: specs/001-batch-mvp-enhancements/e2e-test-plan.md / 1.3

test.describe('Logo visibility across theme states', () => {
  test('toggling back to light restores light header + dark logo', async ({ page }) => {
    // 1. Toggle to dark, then toggle back to light.
    //    - expect: html has data-theme="light"
    //    - expect: header bg restored to light surface
    //    - expect: logo color restored to dark ink
    await page.getByRole('button', { name: 'Switch to dark mode' }).click();
    await page.getByRole('button', { name: 'Switch to light mode' }).click();
    const s = await readHeaderStyles(page);
    expect(s.htmlTheme).toBe('light');
    expect(s.headerBg).toBe('rgba(244, 242, 235, 0.88)');
    expect(s.logoColor).toBe('rgb(34, 29, 22)');
  });
});
