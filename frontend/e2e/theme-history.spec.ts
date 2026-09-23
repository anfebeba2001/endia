import { test, expect, type Page } from '@playwright/test';
import { setupThemesHistoryMock } from './helpers';
import type { ThemeHistory } from '../src/app/models/history.model';

// Feature 011 — read-only history. The `GET /api/themes` mock is installed explicitly per test
// and is deliberately not part of `setupAllMocks` (see helpers.ts).

const THEMES: ThemeHistory[] = [
  {
    themeId: 'theme-newer',
    prompt: 'Photosynthesis',
    createdAt: '2026-09-11T19:52:00',
    subtopics: [
      {
        id: 'sub-001',
        name: 'Light reactions',
        snacks: [
          {
            id: 'snack-1',
            concept: 'Chlorophyll absorbs light',
            metaphor: 'A solar kitchen',
            whyItMatters: 'Prevents a misconception',
            related: ['ATP synthase', 'Electron transport'],
          },
        ],
        quizQuestions: [
          {
            id: 'q-1',
            type: 'MULTIPLE_CHOICE',
            prompt: 'What absorbs light?',
            explanation: 'Chlorophyll drives it.',
          },
        ],
      },
      {
        id: 'sub-002',
        name: 'Calvin cycle',
        snacks: [],
        quizQuestions: [],
      },
    ],
  },
  {
    themeId: 'theme-older',
    prompt: 'Older theme',
    createdAt: '2026-09-10T10:00:00',
    subtopics: [],
  },
];

/** Tab until `selector` matches the active element (keyboard-driven focus-visible). */
async function tabUntilFocused(page: Page, selector: string, maxTabs = 25): Promise<boolean> {
  for (let i = 0; i < maxTabs; i++) {
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

async function readOutline(page: Page): Promise<{ style: string; width: string }> {
  return page.evaluate(() => {
    const el = document.activeElement as HTMLElement;
    const s = getComputedStyle(el);
    return { style: s.outlineStyle, width: s.outlineWidth };
  });
}

test.describe('US1 — Browse created themes from the History page', () => {
  test('header History link is visible and navigates to /history', async ({ page }) => {
    await setupThemesHistoryMock(page, THEMES);
    await page.goto('/');

    const link = page.locator('.app-shell__header .header-link');
    await expect(link).toBeVisible();
    await expect(link).toHaveText(/History/);

    await link.click();
    await expect(page).toHaveURL(/\/history$/);
    await expect(page.locator('app-history')).toBeVisible();
  });

  test('deep link to /history renders without redirect', async ({ page }) => {
    await setupThemesHistoryMock(page, THEMES);

    await page.goto('/history');
    await expect(page.locator('app-history')).toBeVisible();
    await expect(page).toHaveURL(/\/history$/);
    await expect(page.locator('.history-card')).toHaveCount(2);
  });

  test('lists themes newest-first with prompt and formatted createdAt', async ({ page }) => {
    await setupThemesHistoryMock(page, THEMES);
    await page.goto('/history');

    const cards = page.locator('.history-card');
    await expect(cards).toHaveCount(2);

    await expect(cards.nth(0).locator('.history-card__prompt')).toHaveText('Photosynthesis');
    await expect(cards.nth(1).locator('.history-card__prompt')).toHaveText('Older theme');

    // Locally formatted date keeps the calendar year and drops the raw ISO "T" separator.
    await expect(cards.nth(0).locator('.history-card__date')).toContainText('2026');
    await expect(cards.nth(0).locator('.history-card__date')).not.toContainText('T19:52');
  });
});

test.describe('US2 — Read-only nested subtopics, snacks and quiz questions', () => {
  test('expanding a theme shows its nested content and issues no generation requests', async ({ page }) => {
    const postUrls: string[] = [];
    page.on('request', (request) => {
      if (request.method() === 'POST') {
        postUrls.push(request.url());
      }
    });

    await setupThemesHistoryMock(page, THEMES);
    await page.goto('/history');

    const firstCard = page.locator('.history-card').first();
    await firstCard.locator('.history-card__toggle').click();

    await expect(firstCard.locator('.history-subtopic__name').first()).toHaveText('Light reactions');
    await expect(firstCard.locator('.history-snack__concept')).toHaveText('Chlorophyll absorbs light');
    await expect(firstCard.locator('.history-snack__metaphor')).toContainText('solar kitchen');
    await expect(firstCard.locator('.history-snack__why')).toContainText('Prevents a misconception');
    await expect(firstCard.locator('.history-snack__related')).toContainText('ATP synthase');
    await expect(firstCard.locator('.history-question__type')).toContainText('MULTIPLE_CHOICE');
    await expect(firstCard.locator('.history-question__prompt')).toHaveText('What absorbs light?');
    await expect(firstCard.locator('.history-question__explanation')).toContainText('Chlorophyll drives it.');

    // A subtopic with no snacks/questions shows a "nothing generated yet" indication, no error.
    await expect(firstCard.locator('.history-subtopic__empty').first()).toContainText(/nothing generated yet/i);
    await expect(page.locator('.history .error-banner')).toHaveCount(0);

    const generation = postUrls.filter(
      (url) => url.includes('/api/batches') || url.includes('/api/snacks/generate'),
    );
    expect(generation).toEqual([]);
  });
});

test.describe('US3 — Loading, error and empty states', () => {
  test('shows a loading state while in flight and clears it once settled', async ({ page }) => {
    await setupThemesHistoryMock(page, THEMES, { delayMs: 1500 });
    await page.goto('/history');

    await expect(page.locator('.history .loading')).toBeVisible();
    await expect(page.locator('.history-card')).toHaveCount(2);
    await expect(page.locator('.history .loading')).toHaveCount(0);
  });

  test('shows an ErrorService-derived message and no list on failure', async ({ page }) => {
    await setupThemesHistoryMock(page, [], { status: 500, body: { error: 'INTERNAL_ERROR' } });
    await page.goto('/history');

    await expect(page.locator('.history .error-banner')).toContainText(
      'Something went wrong on the server',
    );
    await expect(page.locator('.history-card')).toHaveCount(0);
    await expect(page.locator('.history .loading')).toHaveCount(0);
  });

  test('shows the empty state and zero entries for an empty array', async ({ page }) => {
    await setupThemesHistoryMock(page, []);
    await page.goto('/history');

    await expect(page.locator('.history-empty')).toBeVisible();
    await expect(page.locator('.history-card')).toHaveCount(0);
    await expect(page.locator('.history .error-banner')).toHaveCount(0);
  });
});

test.describe('US4 — Keyboard accessibility', () => {
  test('History header link is keyboard reachable and activatable with visible focus', async ({ page }) => {
    await setupThemesHistoryMock(page, THEMES);
    await page.goto('/');

    expect(await tabUntilFocused(page, '.header-link')).toBe(true);
    const outline = await readOutline(page);
    expect(outline.style).not.toBe('none');
    expect(parseFloat(outline.width)).toBeGreaterThan(0);

    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/history$/);
  });

  test('theme detail control is keyboard reachable and activatable with visible focus', async ({ page }) => {
    await setupThemesHistoryMock(page, THEMES);
    await page.goto('/history');
    await expect(page.locator('.history-card').first()).toBeVisible();

    expect(await tabUntilFocused(page, '.history-card__toggle')).toBe(true);
    const outline = await readOutline(page);
    expect(outline.style).not.toBe('none');
    expect(parseFloat(outline.width)).toBeGreaterThan(0);

    await page.keyboard.press('Enter');
    await expect(page.locator('.history-subtopic__name').first()).toBeVisible();
  });
});

// Feature 012 — the read-only history view renders snack text through the same shared rich-text
// renderer and the same whitelist/sanitization rules as the snack card and the recap
// (FR-023, FR-024, SC-009).

const RICH_THEMES: ThemeHistory[] = [
  {
    themeId: 'theme-rich',
    prompt: 'Photosynthesis',
    createdAt: '2026-09-11T19:52:00',
    subtopics: [
      {
        id: 'sub-rich',
        name: 'Light reactions',
        snacks: [
          {
            id: 'snack-rich',
            concept:
              '**Chlorophyll** absorbs light.\n\nTwo stages:\n- **Capture** light.\n- **Fix** carbon.',
            metaphor: 'A **solar kitchen**.',
            whyItMatters: 'Prevents a **misconception**.',
            related: ['ATP synthase', 'Electron transport'],
          },
        ],
        quizQuestions: [],
      },
    ],
  },
];

const XSS_THEMES: ThemeHistory[] = [
  {
    themeId: 'theme-xss',
    prompt: 'Untrusted',
    createdAt: '2026-09-11T19:52:00',
    subtopics: [
      {
        id: 'sub-xss',
        name: 'Payloads',
        snacks: [
          {
            id: 'snack-xss',
            concept: '<script>window.__xss=1</script>Safe history concept',
            metaphor: '<img src=x onerror="window.__xss=1">History metaphor',
            whyItMatters: '<iframe></iframe> and <style>body{color:red}</style>',
            related: ['Plain'],
          },
        ],
        quizQuestions: [],
      },
    ],
  },
];

test.describe('US5 — rich formatting in the history view (feature 012)', () => {
  test('renders bold, bullets and paragraphs in the expanded history detail', async ({ page }) => {
    await setupThemesHistoryMock(page, RICH_THEMES);
    await page.goto('/history');

    const firstCard = page.locator('.history-card').first();
    await firstCard.locator('.history-card__toggle').click();

    const concept = firstCard.locator('.history-snack__concept');
    await expect(concept.locator('strong')).toHaveText(['Chlorophyll', 'Capture', 'Fix']);
    await expect(concept.locator('ul li')).toHaveCount(2);
    await expect(concept.locator('p')).toHaveCount(2);
    await expect(firstCard.locator('.history-snack')).not.toContainText('**');

    // `related` stays plain labels (no rich-text interpretation).
    await expect(firstCard.locator('.history-snack__related li')).toHaveCount(2);
    await expect(firstCard.locator('.history-snack__related strong')).toHaveCount(0);
  });

  test('neutralizes untrusted markup in the history view', async ({ page }) => {
    await setupThemesHistoryMock(page, XSS_THEMES);
    await page.goto('/history');

    const firstCard = page.locator('.history-card').first();
    await firstCard.locator('.history-card__toggle').click();

    expect(await page.evaluate(() => (window as unknown as { __xss?: number }).__xss)).toBeUndefined();
    for (const selector of ['script', 'img', 'iframe', 'style', 'a']) {
      await expect(firstCard.locator(`.history-snack ${selector}`)).toHaveCount(0);
    }
    await expect(firstCard.locator('.history-snack__concept')).toContainText('Safe history concept');
  });
});
