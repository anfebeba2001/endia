import { Injectable, signal } from '@angular/core';
import { ThemeSuggestResponse, Subtopic } from '../models/theme.model';
import { Snack } from '../models/snack.model';
import { QuizQuestion } from '../models/quiz.model';
import { BatchStatusResponse } from '../models/batch.model';

/**
 * Shares state between the E2E flow pages (home -> subtopics -> snacks -> quiz -> results).
 */
@Injectable({ providedIn: 'root' })
export class AppStateService {
  readonly theme = signal<ThemeSuggestResponse | null>(null);
  readonly subtopics = signal<Subtopic[]>([]);
  readonly snacks = signal<Snack[]>([]);
  readonly selectedSubtopic = signal<Subtopic | null>(null);
  readonly quizQuestions = signal<QuizQuestion[]>([]);
  readonly quizSubTopicId = signal<string | null>(null);

  // Batch / progressive-delivery state (FR-001..004)
  readonly batchId = signal<string | null>(null);
  readonly currentSnackIndex = signal(0);
  readonly quizPrefetched = signal(false);
  /** Prefetched quiz questions keyed by subtopicId (FR-003/SC-002). */
  readonly quizPrefetchBySubtopic = signal<Record<string, QuizQuestion[]>>({});

  setTheme(theme: ThemeSuggestResponse): void {
    this.theme.set(theme);
    this.subtopics.set([...theme.subtopics]);
  }

  setSelectedSubtopics(ids: string[]): void {
    const subs = this.subtopics().filter((s) => ids.includes(s.id));
    this.subtopics.set(subs);
  }

  setBatch(batchId: string): void {
    this.batchId.set(batchId);
    this.currentSnackIndex.set(0);
  }

  advanceSnack(): void {
    this.currentSnackIndex.set(this.currentSnackIndex() + 1);
  }

  resetSnackProgress(): void {
    this.currentSnackIndex.set(0);
  }

  /** Record a prefetched quiz for a subtopic, marking the quiz as prefetched (FR-003). */
  setQuizPrefetch(subtopicId: string, questions: QuizQuestion[]): void {
    this.quizPrefetchBySubtopic.set({ ...this.quizPrefetchBySubtopic(), [subtopicId]: questions });
    this.quizPrefetched.set(true);
  }

  /** Prefetched questions for a given subtopic (empty if absent). */
  getPrefetchedQuiz(subtopicId: string): QuizQuestion[] {
    return this.quizPrefetchBySubtopic()[subtopicId] ?? [];
  }

  reset(): void {
    this.theme.set(null);
    this.subtopics.set([]);
    this.snacks.set([]);
    this.selectedSubtopic.set(null);
    this.quizQuestions.set([]);
    this.quizSubTopicId.set(null);
    this.batchId.set(null);
    this.currentSnackIndex.set(0);
    this.quizPrefetched.set(false);
    this.quizPrefetchBySubtopic.set({});
  }
}
