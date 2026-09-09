import { test as base, type Page, type Route } from '@playwright/test';

// ─── Mock Data ───────────────────────────────────────────────────────────────

const THEME_ID = 'test-theme-001';

const SUBTOPICS = [
  { id: 'sub-001', name: 'Light reactions' },
  { id: 'sub-002', name: 'Calvin cycle' },
  { id: 'sub-003', name: 'Chloroplast structure' },
];

const QUIZ_QUESTIONS = [
  {
    id: 'q-001',
    prompt: 'What is the primary role of chlorophyll in photosynthesis?',
    options: ['Absorbing light energy', 'Storing glucose', 'Releasing oxygen', 'Transporting water'],
  },
  {
    id: 'q-002',
    prompt: 'Where do the light reactions occur in the chloroplast?',
    options: ['Thylakoid membrane', 'Stroma', 'Outer membrane', 'Intermembrane space'],
  },
  {
    id: 'q-003',
    prompt: 'What molecule is the final electron acceptor in the light reactions?',
    options: ['NADP+', 'Oxygen', 'Carbon dioxide', 'ATP'],
  },
  {
    id: 'q-004',
    prompt: 'What is produced directly by the light reactions?',
    options: ['ATP and NADPH', 'Glucose', 'Carbon dioxide', 'RuBP'],
  },
];

const ANSWER_KEY: Record<string, { correctIndex: number; explanation: string }> = {
  'q-001': { correctIndex: 0, explanation: 'Chlorophyll absorbs light energy to drive the photosynthetic process.' },
  'q-002': { correctIndex: 0, explanation: 'The thylakoid membrane contains the photosystems and electron transport chain.' },
  'q-003': { correctIndex: 0, explanation: 'NADP+ accepts electrons at the end of the electron transport chain.' },
  'q-004': { correctIndex: 0, explanation: 'The light reactions produce ATP (energy) and NADPH (reducing power).' },
};

// ─── Route Handlers ──────────────────────────────────────────────────────────

export async function setupThemesSuggestMock(page: Page) {
  await page.route('**/api/themes/suggest', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        themeId: THEME_ID,
        subtopics: SUBTOPICS,
      }),
    });
  });
}

export async function setupSnacksGenerateMock(page: Page) {
  await page.route('**/api/snacks/generate', async (route: Route) => {
    const body = JSON.parse(route.request().postData() || '{}');
    const snacks = (body.subtopicIds || []).map((id: string, i: number) => ({
      id: `snack-${i}`,
      subtopicId: id,
      subtopicName: SUBTOPICS.find((s) => s.id === id)?.name ?? `Subtopic ${i + 1}`,
      concept: `This is the concept explanation for subtopic ${i + 1}. It covers the core idea in one paragraph.`,
      metaphor: `Imagine it like a kitchen — the ingredients are raw materials and the chef is the enzyme that transforms them.`,
      whyItMatters: `Understanding this prevents the common misconception that plants only photosynthesize during the day.`,
      related: ['ATP synthase', 'Electron transport', 'Photophosphorylation'],
    }));
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ snacks }),
    });
  });
}

export async function setupQuizGenerateMock(page: Page) {
  await page.route('**/api/quiz/generate', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        subtopicId: 'sub-001',
        questions: QUIZ_QUESTIONS,
      }),
    });
  });
}

export async function setupQuizAnswerMock(page: Page, answerOverrides?: Record<string, { correctIndex: number; explanation: string }>) {
  const key = answerOverrides || ANSWER_KEY;
  await page.route('**/api/quiz/answer', async (route: Route) => {
    const body = JSON.parse(route.request().postData() || '{}');
    const k = key[body.questionId] || { correctIndex: 0, explanation: 'Test explanation.' };
    const correct = body.chosenIndex === k.correctIndex;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        questionId: body.questionId,
        correct,
        correctIndex: k.correctIndex,
        explanation: k.explanation,
        xpEarned: correct ? 10 : 0,
      }),
    });
  });
}

export async function setupQuizSummaryMock(
  page: Page,
  overrides?: { correctAnswers?: number; totalXp?: number },
) {
  const ca = overrides?.correctAnswers ?? 3;
  const tx = overrides?.totalXp ?? 30;
  await page.route('**/api/quiz/summary/*', async (route: Route) => {
    const url = new URL(route.request().url());
    const subtopicId = url.pathname.split('/').pop();
    const questions = QUIZ_QUESTIONS.map((q, i) => ({
      questionId: q.id,
      prompt: q.prompt,
      options: q.options,
      chosenIndex: 0,
      correct: i < ca,
      correctIndex: 0,
      explanation: ANSWER_KEY[q.id].explanation,
    }));
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        subtopicId,
        totalQuestions: 4,
        correctAnswers: ca,
        totalXp: tx,
        questions,
      }),
    });
  });
}

/** Install ALL mocks for the full happy-path flow. */
export async function setupAllMocks(page: Page) {
  await setupThemesSuggestMock(page);
  await setupSnacksGenerateMock(page);
  await setupQuizGenerateMock(page);
  await setupQuizAnswerMock(page);
  await setupQuizSummaryMock(page);
}

// ─── Angular Helpers ─────────────────────────────────────────────────────────

/**
 * Set the FormControl value and call onSubmit() via Angular debug API.
 * Playwright's native click on the submit button doesn't trigger Angular's (ngSubmit)
 * due to a zone.js/ReactiveFormsModule interaction issue.
 */
export async function angularSubmitHome(page: Page, theme: string) {
  await page.evaluate((t) => {
    const ng = (window as any).ng;
    const el = document.querySelector('app-home');
    if (ng && el) {
      const comp = ng.getComponent(el);
      comp.themeControl.setValue(t);
      comp.onSubmit();
    }
  }, theme);
}

/**
 * Wait for a signal-based component property to change via Angular debug API.
 * Polls every 200ms until the condition is met or timeout.
 */
export async function waitForAngularSignal(
  page: Page,
  selector: string,
  propName: string,
  condition: (val: any) => boolean,
  timeout = 10_000,
) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    const result = await page.evaluate(
      ({ sel, prop }) => {
        const ng = (window as any).ng;
        const el = document.querySelector(sel);
        if (ng && el) {
          const comp = ng.getComponent(el);
          return comp ? comp[prop]() : undefined;
        }
        return undefined;
      },
      { sel: selector, prop: propName },
    );
    if (condition(result)) return result;
    await page.waitForTimeout(200);
  }
  throw new Error(`Timeout waiting for ${selector}.${propName} condition`);
}

// ─── Navigation Helpers ──────────────────────────────────────────────────────

/** Navigate to home, submit a theme → land on /subtopics */
export async function goToSubtopics(page: Page, theme = 'Photosynthesis') {
  await page.goto('/');
  await page.waitForTimeout(2000); // Wait for Angular to bootstrap
  await angularSubmitHome(page, theme);
  await page.waitForURL('**/subtopics');
}

/** From /subtopics, click "Generate snacks" → land on /snacks */
export async function goToSnacks(page: Page) {
  await page.click('button.primary-btn');
  await page.waitForURL('**/snacks');
  // Wait for snacks to load (API mock returns immediately but Angular needs a tick)
  await page.waitForTimeout(500);
}

/** From /snacks, click first "Start Quiz" → land on /quiz */
export async function goToQuiz(page: Page) {
  await page.click('.quiz-btn');
  await page.waitForURL('**/quiz');
  // Wait for quiz questions to load
  await page.waitForTimeout(500);
}

/** Answer a quiz question: click option at 0-based index, wait for feedback, click Next/Results */
export async function answerQuizQuestion(page: Page, optionIndex: number, isLast = false) {
  await page.locator('.option').nth(optionIndex).click();
  await page.locator('.feedback').waitFor({ state: 'visible', timeout: 5000 });
  await page.click('.feedback .primary-btn');
  if (!isLast) {
    await page.waitForTimeout(300);
  }
}

/** Complete the full quiz (4 questions, answer first option each time) */
export async function completeQuiz(page: Page, correctIndices?: number[]) {
  for (let i = 0; i < 4; i++) {
    const idx = correctIndices ? correctIndices[i] : 0;
    await answerQuizQuestion(page, idx, i === 3);
  }
}

// ─── Custom test fixture ─────────────────────────────────────────────────────

export const test = base.extend<{ mockPage: Page }>({
  mockPage: async ({ page }, use) => {
    await setupAllMocks(page);
    await use(page);
  },
});

export { expect } from '@playwright/test';
