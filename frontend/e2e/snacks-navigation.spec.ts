import { type Page } from '@playwright/test';
import {
  test,
  expect,
  goToSubtopics,
  goToSnacks,
  setupThemesSuggestMock,
  setupConfigurableBatchMock,
  batchItemReady,
  batchItemGenerating,
  batchItemFailed,
} from './helpers';

/**
 * US2/US3/US4 — Left-side index, Next/Previous navigation, persisted active index, and
 * accessibility/consistency (FR-007..FR-020, SC-004..SC-008).
 */

/** Assert the displayed card and the single highlighted entry agree (auto-retrying). */
async function expectConsistent(page: Page): Promise<void> {
  await expect
    .poll(async () => {
      const active = page.locator('.snack-index__item--active[aria-current="true"]');
      if ((await active.count()) !== 1) {
        return `active-count:${await active.count()}`;
      }
      const chip = (await page.locator('.snack-card__chip').innerText()).trim();
      const activeText = (await active.innerText()).trim();
      return `${activeText}===${chip}`;
    })
    .toMatch(/^(.+)===\1$/);
}

test.describe('US2 — Left-side snack index', () => {
  test('lists every snack by subtopic name with exactly one active entry', async ({
    mockPage: page,
  }) => {
    await goToSubtopics(page);
    await goToSnacks(page);

    const items = page.locator('.snack-index__item');
    await expect(items).toHaveCount(3);
    await expect(items.nth(0)).toHaveText('Light reactions');
    await expect(items.nth(1)).toHaveText('Calvin cycle');
    await expect(items.nth(2)).toHaveText('Chloroplast structure');

    // FR-008/SC-004: exactly one highlighted entry, matching the displayed card.
    await expect(page.locator('.snack-index__item--active[aria-current="true"]')).toHaveCount(1);
    await expect(page.locator('.snack-index__item--active')).toHaveText('Light reactions');
  });

  test('clicking a READY entry updates the card and highlight together', async ({
    mockPage: page,
  }) => {
    await goToSubtopics(page);
    await goToSnacks(page);

    await page.locator('.snack-index__item').nth(2).click();

    await expect(page.locator('.snack-card__chip')).toHaveText('Chloroplast structure');
    const active = page.locator('.snack-index__item--active[aria-current="true"]');
    await expect(active).toHaveCount(1);
    await expect(active).toHaveText('Chloroplast structure');
  });

  test('non-READY entries are disabled and inert, and Next cannot advance onto them', async ({
    page,
  }) => {
    await setupThemesSuggestMock(page);
    await setupConfigurableBatchMock(page, {
      status: () => ({
        status: 'IN_PROGRESS',
        retrying: true,
        items: [
          batchItemReady('sub-001', 0),
          batchItemGenerating('sub-002', 'AWAITING_MODEL'),
          batchItemFailed('sub-003', 'QUOTA'),
        ],
      }),
    });

    await goToSubtopics(page);
    await page.click('button.primary-btn');
    await page.waitForURL('**/snacks');
    await page.locator('.snack-index__item').first().waitFor({ state: 'visible', timeout: 8000 });

    const items = page.locator('.snack-index__item');
    await expect(items).toHaveCount(3);
    await expect(items.nth(0)).toBeEnabled();
    await expect(items.nth(1)).toBeDisabled();
    await expect(items.nth(2)).toBeDisabled();

    // Clicking a disabled entry is inert.
    await items.nth(1).click({ force: true });
    await expect(page.locator('.snack-card__chip')).toHaveText('Light reactions');
    await expect(page.locator('.snack-index__item--active')).toHaveText('Light reactions');

    // FR-016: Next cannot advance onto the GENERATING item.
    await expect(page.locator('.snack-card .nav-btn--next')).toBeDisabled();
  });
});

test.describe('US3 — Next/Previous and a persistent active index', () => {
  test('Next and Previous move exactly one and disable at the boundaries', async ({
    mockPage: page,
  }) => {
    await goToSubtopics(page);
    await goToSnacks(page);

    const prev = page.locator('.snack-card .nav-btn--prev');
    const next = page.locator('.snack-card .nav-btn--next');

    // Index 0: Previous disabled, Next enabled.
    await expect(prev).toBeDisabled();
    await expect(next).toBeEnabled();

    await next.click();
    await expect(page.locator('.snack-card__chip')).toHaveText('Calvin cycle');
    await expect(prev).toBeEnabled();

    await next.click();
    await expect(page.locator('.snack-card__chip')).toHaveText('Chloroplast structure');
    // Last index: Next disabled.
    await expect(next).toBeDisabled();

    await prev.click();
    await expect(page.locator('.snack-card__chip')).toHaveText('Calvin cycle');
    await expectConsistent(page);
  });

  test('repeated Next reaches every snack with no blank card', async ({ mockPage: page }) => {
    await goToSubtopics(page);
    await goToSnacks(page);

    const expected = ['Light reactions', 'Calvin cycle', 'Chloroplast structure'];
    const chip = page.locator('.snack-card__chip');
    for (let i = 0; i < expected.length; i++) {
      await expect(page.locator('.snack-card')).toHaveCount(1);
      await expect(chip).toHaveText(expected[i]);
      if (i < expected.length - 1) {
        await page.locator('.snack-card .nav-btn--next').click();
      }
    }
    await expect(page.locator('.snack-card')).toHaveCount(1);
  });

  test('active index survives navigating to the quiz and back', async ({ mockPage: page }) => {
    await goToSubtopics(page);
    await goToSnacks(page);

    await page.locator('.snack-index__item').nth(1).click();
    await expect(page.locator('.snack-card__chip')).toHaveText('Calvin cycle');

    await page.click('.quiz-btn');
    await page.waitForURL('**/quiz');
    await page.goBack();
    await page.waitForURL('**/snacks');
    await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });

    await expect(page.locator('.snack-card__chip')).toHaveText('Calvin cycle');
    await expect(page.locator('.snack-index__item--active')).toHaveText('Calvin cycle');
  });

  test('Start Quiz uses the active snack subtopic', async ({ mockPage: page }) => {
    await goToSubtopics(page);
    await goToSnacks(page);

    await page.locator('.snack-index__item').nth(1).click();
    await expect(page.locator('.snack-card__chip')).toHaveText('Calvin cycle');

    const requestPromise = page.waitForRequest((r) => r.url().includes('/api/quiz/generate'));
    await page.click('.quiz-btn');
    const request = await requestPromise;
    expect(request.postDataJSON().subtopicId).toBe('sub-002');
  });
});

test.describe('US4 — Accessible, consistent navigation controls', () => {
  test('index and nav controls are keyboard reachable with a visible focus indicator', async ({
    mockPage: page,
  }) => {
    await goToSubtopics(page);
    await goToSnacks(page);

    const reached = await tabUntil(page, '.snack-index__item');
    expect(reached).toBe(true);
    const focusVisible = await page.evaluate(
      () => document.activeElement?.matches(':focus-visible') ?? false,
    );
    expect(focusVisible).toBe(true);
    const outlineStyle = await page.evaluate(
      () => getComputedStyle(document.activeElement as Element).outlineStyle,
    );
    expect(outlineStyle).not.toBe('none');

    // Enter activates a focused READY entry.
    await page.locator('.snack-index__item').nth(1).focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('.snack-card__chip')).toHaveText('Calvin cycle');
  });

  test('card, highlight and enabled states stay consistent after mixed interactions', async ({
    mockPage: page,
  }) => {
    await goToSubtopics(page);
    await goToSnacks(page);

    const next = page.locator('.snack-card .nav-btn--next');
    const prev = page.locator('.snack-card .nav-btn--prev');

    await next.click();
    await expectConsistent(page);
    await next.click();
    await expectConsistent(page);
    await expect(next).toBeDisabled();

    await prev.click();
    await expectConsistent(page);
    await page.locator('.snack-index__item').nth(0).click();
    await expectConsistent(page);
    await expect(prev).toBeDisabled();
    await expect(next).toBeEnabled();
  });
});

/** Press Tab until the active element matches `selector` (or give up after `max` presses). */
async function tabUntil(page: Page, selector: string, max = 30): Promise<boolean> {
  for (let i = 0; i < max; i++) {
    await page.keyboard.press('Tab');
    const matched = await page.evaluate(
      (sel) => document.activeElement?.matches(sel) ?? false,
      selector,
    );
    if (matched) {
      return true;
    }
  }
  return false;
}
