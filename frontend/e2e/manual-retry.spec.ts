import {
  test,
  expect,
  setupThemesSuggestMock,
  setupConfigurableBatchMock,
  goToSnacksPage,
  batchItemReady,
  batchItemFailed,
} from './helpers';

const ONE = [{ id: 'sub-001', name: 'Light reactions' }];

async function exhaustBudget(
  page: import('@playwright/test').Page,
  state: { createCalls: unknown[] },
) {
  // Wait for the *terminal* exhaustion banner, not the early per-failure banner.
  await expect(page.locator('.error-banner')).toContainText(/after 3 attempts/i, {
    timeout: 15000,
  });
  await expect(page.locator('.error-banner')).toContainText(/QUOTA/);
  expect(state.createCalls.length).toBe(3);
  const retryBtn = page.locator('.error-banner button.link-btn', { hasText: 'Retry' });
  await expect(retryBtn).toBeVisible();
  return retryBtn;
}

test.describe('US4 — manual retry after exhaustion (FR-006)', () => {
  test('one click starts exactly one new cycle, clears the error and renders the snack', async ({
    page,
  }) => {
    await setupThemesSuggestMock(page, ONE);
    let succeed = false;
    const state = await setupConfigurableBatchMock(page, {
      status: (_batchId, subIds) =>
        succeed
          ? { status: 'COMPLETE', items: [batchItemReady(subIds[0], 0)] }
          : { status: 'FAILED', items: [batchItemFailed(subIds[0], 'QUOTA')] },
    });

    await goToSnacksPage(page);
    const retryBtn = await exhaustBudget(page, state);

    // A successful manual retry clears the terminal error and renders the snack.
    succeed = true;
    await retryBtn.click();

    await expect(page.locator('.snack-card')).toBeVisible({ timeout: 8000 });
    expect(state.createCalls.length).toBe(4);
    await expect(page.locator('.error-banner')).toHaveCount(0);
  });

  test('rapid double-click starts at most one new cycle', async ({ page }) => {
    await setupThemesSuggestMock(page, ONE);
    let succeed = false;
    const state = await setupConfigurableBatchMock(page, {
      createDelayMs: 600,
      status: (_batchId, subIds) =>
        succeed
          ? { status: 'COMPLETE', items: [batchItemReady(subIds[0], 0)] }
          : { status: 'FAILED', items: [batchItemFailed(subIds[0], 'QUOTA')] },
    });

    await goToSnacksPage(page);
    const retryBtn = await exhaustBudget(page, state);

    succeed = true;
    await retryBtn.click();
    // Second click lands while the first cycle is in flight and must be ignored.
    await retryBtn.click({ force: true });

    await expect(page.locator('.snack-card')).toBeVisible({ timeout: 8000 });
    expect(state.createCalls.length).toBe(4);
  });

  test('manual retry resets the budget to a fresh 3 automatic attempts (FR-006)', async ({
    page,
  }) => {
    await setupThemesSuggestMock(page, ONE);
    const state = await setupConfigurableBatchMock(page, {
      status: (_batchId, subIds) => ({
        status: 'FAILED',
        items: subIds.map((id) => batchItemFailed(id, 'QUOTA')),
      }),
    });

    await goToSnacksPage(page);
    const retryBtn = await exhaustBudget(page, state);
    expect(state.createCalls.length).toBe(3);

    // Manual retry resets the counter: a fresh 3-attempt cycle runs (3 more creates), then terminal.
    await retryBtn.click();
    await expect.poll(() => state.createCalls.length, { timeout: 20000 }).toBe(6);
    await expect(page.locator('.error-banner')).toContainText(/after 3 attempts/i, {
      timeout: 10000,
    });
    await expect(page.locator('.error-banner')).toContainText(/QUOTA/);

    // No 7th automatic attempt after the second exhaustion.
    await page.waitForTimeout(4000);
    expect(state.createCalls.length).toBe(6);
  });
});
