import { test, expect } from './fixtures';
import { readHeaderStyles } from './styles';

// spec: specs/001-batch-mvp-enhancements/e2e-test-plan.md / 1.1

test.describe('Logo visibility across theme states', () => {
  test('light mode shows light header + dark logo', async ({ page }) => {
    // 1. Load the app (default light theme).
    //    - expect: html has data-theme="light"
    //    - expect: header bg is the light surface
    //    - expect: logo color is dark ink
    const s = await readHeaderStyles(page);
    expect(s.htmlTheme).toBe('light');
    expect(s.headerBg).toBe('rgba(244, 242, 235, 0.88)');
    expect(s.logoColor).toBe('rgb(34, 29, 22)');
  });
});
