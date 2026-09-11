import {
  test,
  expect,
  setupThemesSuggestMock,
  setupConfigurableBatchMock,
  goToSnacksPage,
  batchItemGenerating,
  batchItemReady,
  batchItemFailed,
  type Page,
} from './helpers';

const ONE = [{ id: 'sub-001', name: 'Light reactions' }];
const TWO = [
  { id: 'sub-001', name: 'Light reactions' },
  { id: 'sub-002', name: 'Calvin cycle' },
];

const PHASES = ['RESOLVING_CONTEXT', 'AWAITING_MODEL', 'ASSEMBLING', 'PERSISTING'] as const;
const MESSAGES: Record<string, string> = {
  RESOLVING_CONTEXT: 'Finding the topic context…',
  AWAITING_MODEL: 'Waiting for the AI response…',
  ASSEMBLING: 'Assembling your snack…',
  PERSISTING: 'Saving your snack…',
};

const inProgressText = (page: Page) => page.locator('.loading.state p');

/**
 * US1 — the in-progress message reflects the real backend phase (FR-005..FR-009).
 */
test.describe('US1 — accurate step-by-step snack waiting messages', () => {
  test('renders each phase-specific message across successive polls (FR-005/FR-007/SC-002)', async ({
    page,
  }) => {
    await setupThemesSuggestMock(page, ONE);
    await setupConfigurableBatchMock(page, {
      status: (_batchId, subIds, poll) => {
        const idx = Math.min(Math.max(poll, 1), PHASES.length) - 1;
        return {
          status: 'IN_PROGRESS',
          retrying: true,
          items: [batchItemGenerating(subIds[0], PHASES[idx])],
        };
      },
    });

    await goToSnacksPage(page);

    // Capture the distinct phase messages in the order they are rendered. The pre-first-poll
    // generic text and transient empty samples are ignored; only exact mapped copy is kept.
    const mapped = new Set(Object.values(MESSAGES));
    const observed: string[] = [];
    const deadline = Date.now() + 20_000;
    while (Date.now() < deadline && observed.length < PHASES.length) {
      if (await inProgressText(page).count()) {
        const text = (await inProgressText(page).first().innerText()).trim();
        if (mapped.has(text) && observed[observed.length - 1] !== text) {
          observed.push(text);
        }
      }
      await page.waitForTimeout(100);
    }

    // Each reported phase must render exactly its mapped message, in real phase order
    // (SC-001: no message for a phase the backend is not in; SC-002: all four observable).
    expect(observed).toEqual(PHASES.map((p) => MESSAGES[p]));
  });

  test('replaces the generic preparing message once a phase is reported (FR-005)', async ({
    page,
  }) => {
    await setupThemesSuggestMock(page, ONE);
    await setupConfigurableBatchMock(page, {
      status: (_batchId, subIds) => ({
        status: 'IN_PROGRESS',
        retrying: true,
        items: [batchItemGenerating(subIds[0], 'AWAITING_MODEL')],
      }),
    });

    await goToSnacksPage(page);

    await expect(inProgressText(page).first()).toHaveText(MESSAGES['AWAITING_MODEL'], {
      timeout: 10_000,
    });
    // The single generic in-progress message must not survive once a real phase is reported.
    await expect(page.locator('.loading.state p', { hasText: 'Preparing your snack…' })).toHaveCount(
      0,
    );
  });

  test('tracks the phase per item; one item never shows another item’s phase (FR-008)', async ({
    page,
  }) => {
    await setupThemesSuggestMock(page, TWO);
    await setupConfigurableBatchMock(page, {
      status: (_batchId, subIds) => ({
        status: 'IN_PROGRESS',
        retrying: true,
        items: [
          batchItemGenerating(subIds[0], 'AWAITING_MODEL'),
          batchItemGenerating(subIds[1], 'ASSEMBLING'),
        ],
      }),
    });

    await goToSnacksPage(page);

    await expect(inProgressText(page).first()).toHaveText(MESSAGES['AWAITING_MODEL'], {
      timeout: 10_000,
    });
    // The other item's phase must never bleed into the rendered item (FR-008).
    await expect(
      page.locator('.loading.state p', { hasText: MESSAGES['ASSEMBLING'] }),
    ).toHaveCount(0);
  });

  test('shows no phase message for a READY item (FR-010/SC-004)', async ({ page }) => {
    await setupThemesSuggestMock(page, ONE);
    await setupConfigurableBatchMock(page, {
      status: (_batchId, subIds) => ({ status: 'COMPLETE', items: [batchItemReady(subIds[0], 0)] }),
    });

    await goToSnacksPage(page);
    await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 10_000 });

    await expect(inProgressText(page)).toHaveCount(0);
    for (const message of Object.values(MESSAGES)) {
      await expect(page.locator('.loading.state p', { hasText: message })).toHaveCount(0);
    }
  });

  test('shows no phase message for a FAILED item (error banner instead) (FR-010/SC-004)', async ({
    page,
  }) => {
    await setupThemesSuggestMock(page, ONE);
    await setupConfigurableBatchMock(page, {
      status: (_batchId, subIds) => ({
        status: 'FAILED',
        items: subIds.map((id) => batchItemFailed(id, 'QUOTA')),
      }),
    });

    await goToSnacksPage(page);

    await expect(page.locator('.error-banner')).toBeVisible({ timeout: 10_000 });
    for (const message of Object.values(MESSAGES)) {
      await expect(page.locator('.loading.state p', { hasText: message })).toHaveCount(0);
    }
  });

  test('a retried item re-enters the phases from RESOLVING_CONTEXT (FR-009)', async ({ page }) => {
    await setupThemesSuggestMock(page, ONE);
    const perBatchPolls = new Map<string, number>();
    await setupConfigurableBatchMock(page, {
      status: (batchId, subIds) => {
        const n = (perBatchPolls.get(batchId) ?? 0) + 1;
        perBatchPolls.set(batchId, n);
        if (batchId === 'test-batch-001') {
          return {
            status: 'FAILED',
            items: subIds.map((id) => batchItemFailed(id, 'UNAVAILABLE')),
          };
        }
        const idx = Math.min(n, PHASES.length) - 1;
        return {
          status: 'IN_PROGRESS',
          retrying: true,
          items: [batchItemGenerating(subIds[0], PHASES[idx])],
        };
      },
    });

    await goToSnacksPage(page);

    // The retry cycle is a fresh generation, so the context phase appears again.
    await expect(inProgressText(page).first()).toHaveText(MESSAGES['RESOLVING_CONTEXT'], {
      timeout: 15_000,
    });
  });
});
