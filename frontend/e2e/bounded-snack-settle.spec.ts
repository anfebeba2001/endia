import {
  test,
  expect,
  setupThemesSuggestMock,
  setupConfigurableBatchMock,
  setPollingWindow,
  goToSnacksPage,
  batchItemGenerating,
  batchItemFailed,
} from './helpers';

const ONE = [{ id: 'sub-001', name: 'Light reactions' }];

/**
 * Feature 009 — bounded snack generation.
 *
 * The backend now bounds each subtopic's snack generation and always finalizes a
 * stuck item to FAILED/TIMEOUT so the batch reaches a terminal status. The bound
 * itself is proven by the backend integration test (`BoundedSnackGenerationIntegrationTest`);
 * this spec locks the user-visible contract: a batch that hangs in PENDING/GENERATING
 * eventually settles into a terminal error and stops polling instead of hanging forever.
 *
 * The backend is stubbed at the HTTP boundary with the existing route-mock helpers,
 * modelling the exact status sequence the bounded backend produces. No real hung
 * LLM provider is involved.
 */
test.describe('Feature 009 — bounded snack generation settles the batch screen', () => {
  test('a batch that hangs in GENERATING settles to a terminal TIMEOUT error and stops polling', async ({
    page,
  }) => {
    // Make the client-side polling window much larger than the modelled server bound so the
    // terminal state under test can only come from a server response, not the client timeout.
    await setPollingWindow(page, 30_000);
    await setupThemesSuggestMock(page, ONE);

    const hangPolls = new Map<string, number>();
    const state = await setupConfigurableBatchMock(page, {
      status: (batchId, subIds) => {
        const n = (hangPolls.get(batchId) ?? 0) + 1;
        hangPolls.set(batchId, n);
        // First cycle: the item visibly hangs (the pre-009 failure mode) for two polls...
        if (batchId === 'test-batch-001' && n <= 2) {
          return {
            status: 'IN_PROGRESS',
            retrying: true,
            items: subIds.map((id) => batchItemGenerating(id, 'AWAITING_MODEL')),
          };
        }
        // ...then the per-subtopic bound fires: item finalized FAILED/TIMEOUT, batch terminal.
        return {
          status: 'FAILED',
          items: subIds.map((id) => batchItemFailed(id, 'TIMEOUT')),
        };
      },
    });

    await goToSnacksPage(page);

    // While hung the UI shows progress and no terminal error — it must not settle prematurely.
    await expect(page.locator('.loading.state')).toBeVisible();
    await expect(page.locator('.error-banner')).toHaveCount(0);

    // The bounded outcome surfaces as a terminal error naming the typed cause.
    await expect(page.locator('.error-banner')).toContainText(/TIMEOUT/, { timeout: 20_000 });
    await expect(page.locator('.retry-spinner')).not.toBeVisible();

    // Polling for the original batch has stopped (no infinite loop).
    const original = state.createCalls[0].batchId;
    const polls = state.pollsByBatch.get(original) ?? 0;
    await page.waitForTimeout(3_500);
    expect(state.pollsByBatch.get(original) ?? 0).toBe(polls);
  });
});
