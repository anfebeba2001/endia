import { test as base, type Page } from '@playwright/test';

export { expect } from '@playwright/test';

// Seed fixture: navigate to the app home and reset any persisted theme so every
// scenario starts from a clean, deterministic light-mode state.
export const test = base.extend<{ page: Page }>({
  page: async ({ page }, use) => {
    await page.goto('/');
    await page.evaluate(() => window.localStorage.removeItem('endia-ui-theme'));
    await page.reload();
    await use(page);
  },
});
