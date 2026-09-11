import {
  test,
  expect,
  setupThemesSuggestMock,
  setupConfigurableBatchMock,
  goToSnacksPage,
  batchItemFailed,
  batchItemGenerating,
} from './helpers';

const ONE = [{ id: 'sub-001', name: 'Light reactions' }];
const TWO = [
  { id: 'sub-001', name: 'Light reactions' },
  { id: 'sub-002', name: 'Calvin cycle' },
];

test.describe('US3 — reattempts only on an explicit error, never on a timer (FR-003/004/007)', () => {
  test('no reattempt while IN_PROGRESS; exactly one on an explicit failure; no overlap in flight', async ({
    page,
  }) => {
    await setupThemesSuggestMock(page, TWO);

    let phase: 'inprogress' | 'failed' = 'inprogress';
    const state = await setupConfigurableBatchMock(page, {
      status: (_batchId, subIds) => {
        // A single-target retry batch stays in flight (no terminal) so the guard,
        // not a terminal status, is what prevents a duplicate cycle.
        if (subIds.length === 1) {
          return { status: 'IN_PROGRESS', retrying: true, items: [batchItemGenerating(subIds[0])] };
        }
        if (phase === 'inprogress') {
          return {
            status: 'IN_PROGRESS',
            retrying: true,
            items: subIds.map((id) => batchItemGenerating(id)),
          };
        }
        // Explicit failure for sub-001; sub-002 still generating.
        return {
          status: 'IN_PROGRESS',
          retrying: true,
          items: [batchItemFailed(subIds[0], 'UNAVAILABLE'), batchItemGenerating(subIds[1])],
        };
      },
    });

    await goToSnacksPage(page);

    // Phase A: batch is IN_PROGRESS with no failed item — time passing must not trigger any attempt.
    await page.waitForTimeout(4500);
    expect(state.createCalls.length).toBe(1);

    // Phase B: inject an explicit failure → exactly one reattempt.
    phase = 'failed';
    await expect.poll(() => state.createCalls.length, { timeout: 10000 }).toBe(2);

    // The original batch keeps reporting the stale FAILED item while the retry is in flight;
    // the owner/in-flight guard must prevent an overlapping duplicate cycle.
    await page.waitForTimeout(4500);
    expect(state.createCalls.length).toBe(2);
  });

  test('a failed batch-creation request is an explicit error bounded to 3, never a timer retry (US3.3/FR-003)', async ({
    page,
  }) => {
    await setupThemesSuggestMock(page, ONE);
    const state = await setupConfigurableBatchMock(page, {
      // The initial create succeeds; every reattempt create fails at the HTTP layer.
      createStatusFor: (attempt) => (attempt === 1 ? undefined : 500),
      status: (_batchId, subIds) => ({
        status: 'FAILED',
        items: subIds.map((id) => batchItemFailed(id, 'UNAVAILABLE')),
      }),
    });

    await goToSnacksPage(page);

    // The failed creates consume the same 3-attempt budget and settle terminally.
    await expect(page.locator('.error-banner')).toContainText(/after 3 attempts/i, {
      timeout: 15000,
    });

    // Exactly 3 cycles: 1 successful create + 2 failed create requests.
    expect(state.createCalls.length).toBe(3);
    await page.waitForTimeout(4000);
    expect(state.createCalls.length).toBe(3);
  });
});
