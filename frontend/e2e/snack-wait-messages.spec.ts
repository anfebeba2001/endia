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

    const seen = new Set<string>();
    const deadline = Date.now() + 20_000;
    while (Date.now() < deadline && PHASES.some((p) => !seen.has(MESSAGES[p]))) {
      if (await inProgressText(page).count()) {
        const text = (await inProgressText(page).first().innerText()).trim();
        if (text) {
          seen.add(text);
        }
      }
      await page.waitForTimeout(100);
    }

    for (const phase of PHASES) {
      expect(seen, `expected to observe "${MESSAGES[phase]}"`).toContain(MESSAGES[phase]);
    }
    // The pre-first-poll generic ("Preparing your snack…") is intentionally retained until an
    // item exists; once it does, the phase-specific messages above replace it (FR-005/SC-002).
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
    await expect(inProgressText(page).first()).not.toHaveText(MESSAGES['ASSEMBLING']);
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
