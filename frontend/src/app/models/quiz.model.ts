export interface QuizQuestion {
  id: string;
  prompt: string;
  options: string[];
}

export interface QuizGenerateResponse {
  subtopicId: string;
  questions: QuizQuestion[];
}

export interface QuizAnswerResponse {
  questionId: string;
  correct: boolean;
  correctIndex: number;
  explanation: string;
  xpEarned: number;
}

export interface QuizSummaryQuestion {
  questionId: string;
  prompt: string;
  options: string[];
  chosenIndex: number;
  correct: boolean;
  correctIndex: number;
  explanation: string;
}

export interface QuizSummaryResponse {
  subtopicId: string;
  totalQuestions: number;
  correctAnswers: number;
  totalXp: number;
  questions: QuizSummaryQuestion[];
}
