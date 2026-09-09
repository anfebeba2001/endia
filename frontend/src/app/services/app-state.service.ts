import { Injectable, signal } from '@angular/core';
import { ThemeSuggestResponse, Subtopic } from '../models/theme.model';
import { Snack } from '../models/snack.model';
import { QuizQuestion } from '../models/quiz.model';

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

  setTheme(theme: ThemeSuggestResponse): void {
    this.theme.set(theme);
    this.subtopics.set([...theme.subtopics]);
  }

  setSelectedSubtopics(ids: string[]): void {
    const subs = this.subtopics().filter((s) => ids.includes(s.id));
    this.subtopics.set(subs);
  }

  reset(): void {
    this.theme.set(null);
    this.subtopics.set([]);
    this.snacks.set([]);
    this.selectedSubtopic.set(null);
    this.quizQuestions.set([]);
    this.quizSubTopicId.set(null);
  }
}
