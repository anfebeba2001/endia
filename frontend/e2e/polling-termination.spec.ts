import {
  test,
  expect,
  setupThemesSuggestMock,
  setupConfigurableBatchMock,
  setPollingWindow,
  goToSnacksPage,
  batchItemReady,
  batchItemFailed,
  batchItemGenerating,
} from './helpers';

const ONE = [{ id: 'sub-001', name: 'Light reactions' }];
const TWO = [
  { id: 'sub-001', name: 'Light reactions' },
  { id: 'sub-002', name: 'Calvin cycle' },
];

test.describe('US2 — polling terminates on every terminal state (FR-008/FR-009)', () => {
  test('stops polling on PARTIAL', async ({ page }) => {
    await setupThemesSuggestMock(page, TWO);
    const state = await setupConfigurableBatchMock(page, {
      status: (batchId, subIds) => {
        if (batchId !== 'test-batch-001') {
          return { status: 'IN_PROGRESS', retrying: true, items: [batchItemGenerating(subIds[0])] };
        }
        // Mixed success: first snack READY, second has a failed quiz (PARTIAL) but no
        // failed snack, so no reattempt is triggered — polling alone is under test.
        return {
          status: 'PARTIAL',
          items: [
            batchItemReady('sub-001', 0),
            { ...batchItemReady('sub-002', 1), quizStatus: 'FAILED', quizCause: 'QUOTA' },
          ],
        };
      },
    });

    await goToSnacksPage(page);
    await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });

    const original = state.createCalls[0].batchId;
    const polls = state.pollsByBatch.get(original) ?? 0;
    await page.waitForTimeout(3500);
    expect(state.pollsByBatch.get(original) ?? 0).toBe(polls);
  });

  test('stops polling on COMPLETE', async ({ page }) => {
    await setupThemesSuggestMock(page, ONE);
    const state = await setupConfigurableBatchMock(page, {
      status: (_batchId, subIds) => ({ status: 'COMPLETE', items: [batchItemReady(subIds[0], 0)] }),
    });

    await goToSnacksPage(page);
    await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });

    const original = state.createCalls[0].batchId;
    const polls = state.pollsByBatch.get(original) ?? 0;
    await page.waitForTimeout(3500);
    expect(state.pollsByBatch.get(original) ?? 0).toBe(polls);
  });

  test('stops polling on FAILED', async ({ page }) => {
    await setupThemesSuggestMock(page, ONE);
    const state = await setupConfigurableBatchMock(page, {
      status: (batchId, subIds) => {
        if (batchId !== 'test-batch-001') {
          // A retry batch stays in flight; the original batch must still stop polling.
          return { status: 'IN_PROGRESS', retrying: true, items: [batchItemGenerating(subIds[0])] };
        }
        return { status: 'FAILED', items: [batchItemFailed(subIds[0])] };
      },
    });

    await goToSnacksPage(page);
    await page.locator('.error-banner').waitFor({ state: 'visible', timeout: 8000 });

    const original = state.createCalls[0].batchId;
    const polls = state.pollsByBatch.get(original) ?? 0;
    await page.waitForTimeout(3500);
    expect(state.pollsByBatch.get(original) ?? 0).toBe(polls);
  });

  test('stuck IN_PROGRESS terminates with a terminal error when the window elapses (FR-010/SC-004)', async ({
    page,
  }) => {
    await setPollingWindow(page, 1000);
    await setupThemesSuggestMock(page, ONE);
    const state = await setupConfigurableBatchMock(page, {
      status: (_batchId, subIds) => ({
        status: 'IN_PROGRESS',
        retrying: true,
        items: [batchItemGenerating(subIds[0])],
      }),
    });

    await goToSnacksPage(page);
    await expect(page.locator('.error-banner')).toBeVisible({ timeout: 8000 });
    await expect(page.locator('.error-banner')).toContainText(/timed out|timeout|too long/i);

    const count = state.pollCount;
    await page.waitForTimeout(2500);
    expect(state.pollCount).toBe(count);
  });
});
