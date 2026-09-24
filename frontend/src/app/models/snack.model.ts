/** A key term of a snack with a short (at most 20 words) definition (feature 013; feature 015). */
export interface SnackKeyword {
  term: string;
  definition: string;
}

export interface Snack {
  id: string;
  subtopicId: string;
  subtopicName: string;
  concept: string;
  metaphor: string;
  whyItMatters: string;
  related: string[];
  /** Optional for graceful degradation; use sites coalesce with `?? []` (FR-024). */
  keywords?: SnackKeyword[];
}

export interface SnackGenerateResponse {
  snacks: Snack[];
}
