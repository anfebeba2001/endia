// Quiz question types (FR-005..008)

import type { SnackKeyword } from './snack.model';

export type QuestionType = 'MULTIPLE_CHOICE' | 'DRAG_DROP';
export type DragDropVariant = 'SEQUENCING' | 'CLASSIFICATION' | 'MATCHING';

export interface DragDropPayload {
  items: string[];
  categories?: string[];
  rightItems?: string[];
}

export interface QuizQuestion {
  id: string;
  type: QuestionType;
  prompt: string;
  options: string[];
  variant?: DragDropVariant | null;
  dragDrop?: DragDropPayload | null;
}

export interface QuizGenerateResponse {
  subtopicId: string;
  variant?: DragDropVariant | null;
  questions: QuizQuestion[];
}

export interface QuizAnswerResponse {
  questionId: string;
  type: QuestionType;
  correct: boolean;
  correctIndex: number | null;
  correctArrangement?: string[] | null;
  explanation: string;
  xpEarned: number;
}

export interface QuizSummaryQuestion {
  questionId: string;
  type: QuestionType;
  prompt: string;
  options: string[];
  chosenIndex: number | null;
  correct: boolean;
  correctIndex: number | null;
  variant?: DragDropVariant | null;
  correctArrangement?: string[] | null;
  submittedArrangement?: string[] | null;
  explanation: string;
}

export interface Recap {
  subtopicId: string;
  subtopicName: string;
  snack: {
    id: string;
    subtopicId: string;
    subtopicName: string;
    concept: string;
    metaphor: string;
    whyItMatters: string;
    related: string[];
    keywords?: SnackKeyword[];
  } | null;
}

export interface QuizSummaryResponse {
  subtopicId: string;
  totalQuestions: number;
  correctAnswers: number;
  totalXp: number;
  questions: QuizSummaryQuestion[];
  recap?: Recap | null;
}
