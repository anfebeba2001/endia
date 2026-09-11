import {
  test,
  expect,
  setupThemesSuggestMock,
  setupConfigurableBatchMock,
  goToSnacksPage,
  batchItemFailed,
  batchItemReady,
} from './helpers';

const ONE = [{ id: 'sub-001', name: 'Light reactions' }];

test.describe('US1 — automatic reattempts are bounded to 3 (FR-001/002/005)', () => {
  test('makes exactly 3 attempts, then a terminal error naming the cause, and never a 4th', async ({
    page,
  }) => {
    await setupThemesSuggestMock(page, ONE);
    const state = await setupConfigurableBatchMock(page, {
      // Every generation attempt fails.
      status: (_batchId, subIds) => ({
        status: 'FAILED',
        items: subIds.map((id) => batchItemFailed(id, 'QUOTA')),
      }),
    });

    await goToSnacksPage(page);

    // Terminal error appears once the 3rd cycle has failed.
    await expect(page.locator('.error-banner')).toContainText(/QUOTA/, { timeout: 15000 });
    await expect(page.locator('.error-banner')).toContainText(/3/);
    await expect(page.locator('.error-banner')).toContainText(/attempt/i);

    // Exactly 3 cycles for the target: initial + 2 automatic reattempts.
    expect(state.createCalls.length).toBe(3);
    expect(state.createCalls.every((c) => c.subtopicIds.length === 1)).toBe(true);

    // Spinner has stopped and no background activity continues.
    await expect(page.locator('.retry-spinner')).not.toBeVisible();
    await page.waitForTimeout(4000);
    expect(state.createCalls.length).toBe(3);
  });

  test('succeeds on a later attempt within the budget and stops retrying (US1.4/SC-001)', async ({
    page,
  }) => {
    await setupThemesSuggestMock(page, ONE);
    const state = await setupConfigurableBatchMock(page, {
      // The initial batch fails; the first automatic reattempt succeeds.
      status: (batchId, subIds) =>
        batchId === 'test-batch-001'
          ? { status: 'FAILED', items: subIds.map((id) => batchItemFailed(id, 'UNAVAILABLE')) }
          : { status: 'COMPLETE', items: [batchItemReady(subIds[0], 0)] },
    });

    await goToSnacksPage(page);

    // The snack renders and the transient failure banner clears.
    await expect(page.locator('.snack-card')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.error-banner')).toHaveCount(0);

    // Exactly 2 cycles: initial failure + one successful reattempt; budget not consumed further.
    expect(state.createCalls.length).toBe(2);
    await page.waitForTimeout(4000);
    expect(state.createCalls.length).toBe(2);
  });
});
