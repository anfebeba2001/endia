/**
 * Meaning-snack view models (feature 014), mirroring the `/api/meaning-snacks` contract.
 *
 * A meaning snack is a distinct kind: it carries `kind: 'MEANING'`, its source term/definition,
 * and the generated content, but deliberately **no** quiz fields (`subtopicId` quiz target,
 * `quizStatus`, `quizQuestions`) and **no** `keywords` (FR-008/FR-016/SC-005).
 */

export type MeaningSnackKind = 'MEANING';

export interface MeaningSnack {
  id: string;
  kind: MeaningSnackKind;
  themeId: string;
  sourceSubtopicId: string;
  sourceSubtopicName: string;
  sourceTerm: string;
  sourceDefinition: string;
  concept: string;
  metaphor: string;
  whyItMatters: string;
  related: string[];
  createdAt: string;
}

export interface MeaningSnackGenerateResponse {
  alreadyExisted: boolean;
  meaningSnack: MeaningSnack;
}

export interface MeaningSnackListResponse {
  themeId: string;
  meaningSnacks: MeaningSnack[];
}
