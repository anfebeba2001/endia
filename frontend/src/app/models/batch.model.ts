// Batch / progressive snack delivery model (FR-001..004)

import { QuizQuestion } from './quiz.model';

export type GenerationStatus = 'PENDING' | 'GENERATING' | 'READY' | 'FAILED';
export type BatchStatus = 'IN_PROGRESS' | 'PARTIAL' | 'COMPLETE' | 'FAILED';
export type LlmErrorCause = 'UNAVAILABLE' | 'TIMEOUT' | 'NETWORK' | 'QUOTA' | 'MALFORMED';

export interface BatchItem {
  subtopicId: string;
  subtopicName: string;
  snackStatus: GenerationStatus;
  snack: {
    id: string;
    subtopicId: string;
    subtopicName: string;
    concept: string;
    metaphor: string;
    whyItMatters: string;
    related: string[];
  } | null;
  snackCause: LlmErrorCause | null;
  quizStatus: GenerationStatus;
  quizCause: LlmErrorCause | null;
  /** Prefetched quiz for this subtopic, present only when quizStatus === 'READY' (FR-003/SC-002). */
  quizQuestions?: QuizQuestion[] | null;
}

export interface BatchStatusResponse {
  batchId: string;
  themeId: string;
  status: BatchStatus;
  retrying: boolean;
  items: BatchItem[];
}

export interface BatchCreateResponse {
  batchId: string;
  themeId: string;
  status: BatchStatus;
  items: { subtopicId: string; snackStatus: GenerationStatus; quizStatus: GenerationStatus }[];
}
