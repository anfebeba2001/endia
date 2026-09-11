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
    options: [
      'Absorbing light energy',
      'Storing glucose',
      'Releasing oxygen',
      'Transporting water',
    ],
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
  'q-001': {
    correctIndex: 0,
    explanation: 'Chlorophyll absorbs light energy to drive the photosynthetic process.',
  },
  'q-002': {
    correctIndex: 0,
    explanation: 'The thylakoid membrane contains the photosystems and electron transport chain.',
  },
  'q-003': {
    correctIndex: 0,
    explanation: 'NADP+ accepts electrons at the end of the electron transport chain.',
  },
  'q-004': {
    correctIndex: 0,
    explanation: 'The light reactions produce ATP (energy) and NADPH (reducing power).',
  },
};

// ─── Route Handlers ──────────────────────────────────────────────────────────

export async function setupThemesSuggestMock(page: Page, subtopics = SUBTOPICS) {
  await page.route('**/api/themes/suggest', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        themeId: THEME_ID,
        subtopics,
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

export async function setupQuizAnswerMock(
  page: Page,
  answerOverrides?: Record<string, { correctIndex: number; explanation: string }>,
) {
  const key = answerOverrides || ANSWER_KEY;
  await page.route('**/api/quiz/answer', async (route: Route) => {
    const body = JSON.parse(route.request().postData() || '{}');
    const k = key[body.questionId] || { correctIndex: 0, explanation: 'Test explanation.' };
    if (body.arrangement) {
      // drag-drop answers always correct in the mock
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          questionId: body.questionId,
          type: 'DRAG_DROP',
          correct: true,
          correctIndex: null,
          correctArrangement: body.arrangement,
          explanation: 'Test explanation.',
          xpEarned: 10,
        }),
      });
      return;
    }
    const correct = body.chosenIndex === k.correctIndex;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        questionId: body.questionId,
        type: 'MULTIPLE_CHOICE',
        correct,
        correctIndex: k.correctIndex,
        correctArrangement: null,
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
  await setupBatchMock(page);
  await setupQuizGenerateMock(page);
  await setupQuizAnswerMock(page);
  await setupQuizSummaryMock(page);
}

/** Build a snack object for a subtopic id (mirrors backend snack shape). */
function buildSnack(id: string, i: number) {
  return {
    id: `snack-${i}`,
    subtopicId: id,
    subtopicName: SUBTOPICS.find((s) => s.id === id)?.name ?? `Subtopic ${i + 1}`,
    concept: `This is the concept explanation for subtopic ${i + 1}. It covers the core idea in one paragraph.`,
    metaphor: `Imagine it like a kitchen — the ingredients are raw materials and the chef is the enzyme that transforms them.`,
    whyItMatters: `Understanding this prevents the common misconception that plants only photosynthesize during the day.`,
    related: ['ATP synthase', 'Electron transport', 'Photophosphorylation'],
  };
}

/**
 * Mock the batch endpoints used by the progressive snacks page (FR-001..004).
 * POST /api/batches → returns 202 immediately; GET /api/batches/{id} → returns
 * a COMPLETE status with every snack READY (so the page fully renders and polling stops).
 */
export async function setupBatchMock(page: Page, opts?: { snackStatus?: string }) {
  const snackStatus = opts?.snackStatus ?? 'READY';
  await page.route('**/api/batches', async (route: Route) => {
    const body = JSON.parse(route.request().postData() || '{}');
    const subIds: string[] = body.subtopicIds || [];
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        batchId: 'test-batch-001',
        themeId: THEME_ID,
        status: 'IN_PROGRESS',
        items: subIds.map((id) => ({
          subtopicId: id,
          snackStatus: 'PENDING',
          quizStatus: 'PENDING',
        })),
      }),
    });
  });
  await page.route('**/api/batches/*', async (route: Route) => {
    const url = new URL(route.request().url());
    const requestedIds = url.search ? [] : undefined;
    let items = [];
    // Reconstruct items from the batch request captured above is complex; derive from subtopics.
    items = SUBTOPICS.map((s, i) => ({
      subtopicId: s.id,
      subtopicName: s.name,
      snackStatus,
      snack: snackStatus === 'READY' ? buildSnack(s.id, i) : null,
      snackCause: snackStatus === 'FAILED' ? 'QUOTA' : null,
      quizStatus: 'READY',
      quizCause: null,
    }));
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        batchId: 'test-batch-001',
        themeId: THEME_ID,
        // Terminality is negative (FR-008): READY → COMPLETE, FAILED → FAILED, else IN_PROGRESS.
        status:
          snackStatus === 'READY'
            ? 'COMPLETE'
            : snackStatus === 'FAILED'
              ? 'FAILED'
              : 'IN_PROGRESS',
        retrying: false,
        items,
      }),
    });
  });
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
  // Batch is created then polled (~1.5s interval); wait for the first READY snack card.
  await page.locator('.snack-card').waitFor({ state: 'visible', timeout: 8000 });
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

// ─── Configurable batch mocks (feature 002 — bounded reattempts) ─────────────

export interface BatchMockItem {
  subtopicId: string;
  subtopicName?: string;
  snackStatus: string;
  snack?: unknown | null;
  snackCause?: string | null;
  quizStatus?: string;
  quizCause?: string | null;
}

export interface BatchMockStatus {
  status: string;
  retrying?: boolean;
  items: BatchMockItem[];
}

export interface BatchMockOptions {
  /** Called for every GET /api/batches/{id} with the batch's subtopic ids and the global poll count. */
  status: (batchId: string, subtopicIds: string[], poll: number) => BatchMockStatus;
  /** HTTP status for POST /api/batches (default 202). Use >=400 to simulate a create failure. */
  createStatus?: number;
  /**
   * Per-attempt POST /api/batches status (1-based call number). Return >=400 to fail that
   * specific create, or undefined/2xx to succeed. Takes precedence over `createStatus`.
   */
  createStatusFor?: (attempt: number) => number | undefined;
  /** Delay (ms) before fulfilling POST /api/batches. */
  createDelayMs?: number;
}

export interface BatchMockState {
  /** All POST /api/batches calls in order (batchId is '' when the create was made to fail). */
  createCalls: { batchId: string; subtopicIds: string[] }[];
  /** Total GET /api/batches/{id} calls across every batch. */
  readonly pollCount: number;
  /** GET count per batch id. */
  pollsByBatch: Map<string, number>;
  /** Resolve a batch id to the subtopic ids it was created for. */
  subtopicsFor: (batchId: string) => string[];
}

export function snackFor(subtopicId: string, i = 0) {
  return buildSnack(subtopicId, i);
}

export function batchItemReady(subtopicId: string, i = 0): BatchMockItem {
  return {
    subtopicId,
    subtopicName: SUBTOPICS.find((s) => s.id === subtopicId)?.name ?? subtopicId,
    snackStatus: 'READY',
    snack: buildSnack(subtopicId, i),
    snackCause: null,
    quizStatus: 'READY',
    quizCause: null,
  };
}

export function batchItemFailed(subtopicId: string, cause = 'QUOTA'): BatchMockItem {
  return {
    subtopicId,
    subtopicName: SUBTOPICS.find((s) => s.id === subtopicId)?.name ?? subtopicId,
    snackStatus: 'FAILED',
    snack: null,
    snackCause: cause,
    quizStatus: 'READY',
    quizCause: null,
  };
}

export function batchItemGenerating(subtopicId: string): BatchMockItem {
  return {
    subtopicId,
    subtopicName: SUBTOPICS.find((s) => s.id === subtopicId)?.name ?? subtopicId,
    snackStatus: 'GENERATING',
    snack: null,
    snackCause: null,
    quizStatus: 'GENERATING',
    quizCause: null,
  };
}

/**
 * Configurable batch endpoint mock for feature 002. Tracks POST/GET counts per batch so
 * specs can assert the bounded attempt count, polling termination, and duplicate guards.
 */
export async function setupConfigurableBatchMock(
  page: Page,
  opts: BatchMockOptions,
): Promise<BatchMockState> {
  const createCalls: { batchId: string; subtopicIds: string[] }[] = [];
  const subtopicsByBatch = new Map<string, string[]>();
  const pollsByBatch = new Map<string, number>();
  let pollCount = 0;
  let seq = 0;

  await page.route('**/api/batches', async (route: Route) => {
    if (route.request().method() !== 'POST') {
      return route.continue();
    }
    const body = JSON.parse(route.request().postData() || '{}');
    const subIds: string[] = body.subtopicIds || [];
    const attempt = createCalls.length + 1;
    const status = opts.createStatusFor ? opts.createStatusFor(attempt) : opts.createStatus;
    if (status && status >= 400) {
      createCalls.push({ batchId: '', subtopicIds: subIds });
      await route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'INTERNAL', message: 'Batch creation failed' }),
      });
      return;
    }
    if (opts.createDelayMs) {
      await new Promise((r) => setTimeout(r, opts.createDelayMs));
    }
    seq += 1;
    const batchId = `test-batch-${String(seq).padStart(3, '0')}`;
    createCalls.push({ batchId, subtopicIds: subIds });
    subtopicsByBatch.set(batchId, subIds);
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        batchId,
        themeId: THEME_ID,
        status: 'IN_PROGRESS',
        items: subIds.map((id) => ({
          subtopicId: id,
          snackStatus: 'PENDING',
          quizStatus: 'PENDING',
        })),
      }),
    });
  });

  await page.route('**/api/batches/*', async (route: Route) => {
    pollCount += 1;
    const url = new URL(route.request().url());
    const batchId = url.pathname.split('/').pop() ?? '';
    const subIds = subtopicsByBatch.get(batchId) ?? SUBTOPICS.map((s) => s.id);
    pollsByBatch.set(batchId, (pollsByBatch.get(batchId) ?? 0) + 1);
    const resp = opts.status(batchId, subIds, pollCount);
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        batchId,
        themeId: THEME_ID,
        status: resp.status,
        retrying: resp.retrying ?? false,
        items: resp.items,
      }),
    });
  });

  return {
    createCalls,
    get pollCount() {
      return pollCount;
    },
    pollsByBatch,
    subtopicsFor: (batchId: string) => subtopicsByBatch.get(batchId) ?? [],
  };
}

/**
 * Override the frontend polling window before the app boots. Reads
 * `window.__MAX_POLLING_WINDOW_MS__` (see app.config.ts) so E2E can exercise the
 * bounded-lifetime path quickly instead of waiting the 120 s default.
 */
export async function setPollingWindow(page: Page, ms: number) {
  await page.addInitScript((value) => {
    (window as unknown as Record<string, unknown>)['__MAX_POLLING_WINDOW_MS__'] = value;
  }, ms);
}

// ─── Navigation (feature 002 helpers) ────────────────────────────────────────

/** Navigate home → subtopics → snacks without requiring a READY snack card. */
export async function goToSnacksPage(page: Page, theme = 'Photosynthesis') {
  await goToSubtopics(page, theme);
  await page.click('button.primary-btn');
  await page.waitForURL('**/snacks');
}

// ─── Custom test fixture ─────────────────────────────────────────────────────

export const test = base.extend<{ mockPage: Page }>({
  mockPage: async ({ page }, use) => {
    await setupAllMocks(page);
    await use(page);
  },
});

export { expect } from '@playwright/test';
