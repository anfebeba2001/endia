export interface Snack {
  id: string;
  subtopicId: string;
  subtopicName: string;
  concept: string;
  metaphor: string;
  whyItMatters: string;
  related: string[];
}

export interface SnackGenerateResponse {
  snacks: Snack[];
}
