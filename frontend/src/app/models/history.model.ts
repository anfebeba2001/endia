import type { QuestionType } from './quiz.model';
import type { SnackKeyword } from './snack.model';

/** Read-only history model (feature 011), mirroring `GET /api/themes`. */

export interface HistorySnack {
  id: string;
  concept: string;
  metaphor: string;
  whyItMatters: string;
  related: string[];
  keywords?: SnackKeyword[];
}

export interface HistoryQuestion {
  id: string;
  type: QuestionType;
  prompt: string;
  explanation: string;
}

export interface HistorySubtopic {
  id: string;
  name: string;
  snacks: HistorySnack[];
  quizQuestions: HistoryQuestion[];
}

export interface ThemeHistory {
  themeId: string;
  prompt: string;
  createdAt: string;
  subtopics: HistorySubtopic[];
}
