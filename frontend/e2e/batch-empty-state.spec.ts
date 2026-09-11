import {
  test,
  expect,
  setupThemesSuggestMock,
  setupConfigurableBatchMock,
  setPollingWindow,
  goToSnacksPage,
  batchItemReady,
} from './helpers';

const ONE = [{ id: 'sub-001', name: 'Light reactions' }];

/**
 * US3 — no premature empty-state flash and terminal-only empty state (FR-009/FR-010/FR-011).
 * The empty state must never render while a batch is being created, before the first poll
 * response, or while it is still IN_PROGRESS.
 */
test.describe('US3 — empty-state gating (FR-009/FR-010/FR-011)', () => {
  test('does not flash the empty state before the first poll response (FR-010)', async ({ page }) => {
    await setupThemesSuggestMock(page, ONE);
    await setupConfigurableBatchMock(page, {
      // Delay only the first poll so the create→first-poll window is observable.
      pollDelayMs: (poll) => (poll === 1 ? 2000 : 0),
      status: (_batchId, subIds) => ({ status: 'COMPLETE', items: [batchItemReady(subIds[0], 0)] }),
    });

    await goToSnacksPage(page);

    // Create has succeeded, the first (delayed) poll has not returned: progress, not empty state.
    await expect(page.locator('.empty-state')).toBeHidden();
    await expect(page.locator('.loading.state')).toBeVisible();

    // Once the first poll returns, the ready snack renders and the empty state stays hidden.
    await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });
    await expect(page.locator('.empty-state')).toBeHidden();
  });

  test('suppresses the empty state while IN_PROGRESS with an empty item list (FR-009)', async ({ page }) => {
    await setPollingWindow(page, 5000);
    await setupThemesSuggestMock(page, ONE);
    await setupConfigurableBatchMock(page, {
      status: () => ({ status: 'IN_PROGRESS', retrying: true, items: [] }),
    });

    await goToSnacksPage(page);

    await expect(page.locator('.empty-state')).toBeHidden();
    await expect(page.locator('.loading.state')).toBeVisible();
    await page.waitForTimeout(2000);
    await expect(page.locator('.empty-state')).toBeHidden();
  });

  test('shows the empty state only for a terminal, zero-ready batch and stops polling (FR-011)', async ({
    page,
  }) => {
    await setupThemesSuggestMock(page, ONE);
    const state = await setupConfigurableBatchMock(page, {
      status: () => ({ status: 'FAILED', items: [] }),
    });

    await goToSnacksPage(page);

    await expect(page.locator('.empty-state')).toBeVisible({ timeout: 8000 });

    // Terminal status → polling stops (no further GETs for the original batch).
    const original = state.createCalls[0].batchId;
    const polls = state.pollsByBatch.get(original) ?? 0;
    await page.waitForTimeout(3000);
    expect(state.pollsByBatch.get(original) ?? 0).toBe(polls);
  });
});
