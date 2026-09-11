// Batch / progressive snack delivery model (FR-001..004)

import { QuizQuestion } from './quiz.model';

export type GenerationStatus = 'PENDING' | 'GENERATING' | 'READY' | 'FAILED';
export type BatchStatus = 'IN_PROGRESS' | 'PARTIAL' | 'COMPLETE' | 'FAILED';
export type LlmErrorCause = 'UNAVAILABLE' | 'TIMEOUT' | 'NETWORK' | 'QUOTA' | 'MALFORMED';

/** Real snack generation phase reported by the backend while an item is GENERATING (feature 008). */
export type SnackGenerationPhase =
  | 'RESOLVING_CONTEXT'
  | 'AWAITING_MODEL'
  | 'ASSEMBLING'
  | 'PERSISTING';

/** Static, user-safe message per phase. Never derived from a timer and carries no generated content. */
export const PHASE_MESSAGES: Record<SnackGenerationPhase, string> = {
  RESOLVING_CONTEXT: 'Finding the topic context…',
  AWAITING_MODEL: 'Waiting for the AI response…',
  ASSEMBLING: 'Assembling your snack…',
  PERSISTING: 'Saving your snack…',
};

/** Map a reported phase to its message; unknown/absent phases return the generic fallback. */
export function phaseMessage(phase: SnackGenerationPhase | null | undefined): string {
  return phase && PHASE_MESSAGES[phase] ? PHASE_MESSAGES[phase] : 'Preparing your snack…';
}

export interface BatchItem {
  subtopicId: string;
  subtopicName: string;
  snackStatus: GenerationStatus;
  /** Current real generation phase, non-null only while snackStatus === 'GENERATING'. */
  snackPhase?: SnackGenerationPhase | null;
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
