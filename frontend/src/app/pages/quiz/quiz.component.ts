import { ChangeDetectionStrategy, Component, Signal, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppStateService } from '../../services/app-state.service';
import { QuizService } from '../../services/quiz.service';
import { ErrorService } from '../../services/error.service';
import { QuizQuestion, QuizAnswerResponse } from '../../models/quiz.model';
import { Subtopic } from '../../models/theme.model';

@Component({
  selector: 'app-quiz',
  standalone: true,
  templateUrl: './quiz.component.html',
  styleUrl: './quiz.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizComponent {
  readonly loadingQuestions = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly currentIndex = signal(0);
  readonly submitting = signal(false);
  readonly selectedOption = signal<number | null>(null);
  readonly feedback = signal<QuizAnswerResponse | null>(null);
  readonly results = signal<QuizAnswerResponse[]>([]);

  readonly currentQuestion: Signal<QuizQuestion | null> = computed(() => {
    const questions = this.appState.quizQuestions();
    const index = this.currentIndex();
    return questions.length ? questions[index] ?? null : null;
  });

  readonly progress = computed(() => {
    const total = this.appState.quizQuestions().length;
    return total ? Math.round(((this.currentIndex() + (this.feedback() ? 1 : 0)) / total) * 100) : 0;
  });

  constructor(
    public appState: AppStateService,
    private quizService: QuizService,
    private errorService: ErrorService,
    private router: Router
  ) {
    void this.init();
  }

  get subtopic(): Subtopic | null {
    return this.appState.selectedSubtopic();
  }

  private get themeId(): string | null {
    return this.appState.theme()?.themeId ?? null;
  }

  private async init(): Promise<void> {
    const subtopic = this.subtopic;
    const themeId = this.themeId;
    if (!subtopic || !themeId) {
      this.router.navigate(['/snacks']);
      return;
    }

    if (this.appState.quizQuestions().length === 0) {
      this.quizService.generate(themeId, subtopic.id, 4).subscribe({
        next: (response) => {
          this.appState.quizQuestions.set(response.questions);
          this.appState.quizSubTopicId.set(subtopic.id);
          this.loadingQuestions.set(false);
        },
        error: (err) => {
          this.loadingQuestions.set(false);
          this.errorMessage.set(this.errorService.getMessage(err));
        },
      });
    } else {
      this.loadingQuestions.set(false);
    }
  }

  selectOption(index: number): void {
    if (this.submitting() || this.feedback() !== null) {
      return;
    }
    this.selectedOption.set(index);
    this.submitAnswer(index);
  }

  private submitAnswer(index: number): void {
    const question = this.currentQuestion();
    if (!question) {
      return;
    }
    this.submitting.set(true);
    this.quizService.answer(question.id, index).subscribe({
      next: (answer) => {
        this.feedback.set(answer);
        const updated = [...this.results()];
        const existing = updated.findIndex((r) => r.questionId === answer.questionId);
        if (existing >= 0) {
          updated[existing] = answer;
        } else {
          updated.push(answer);
        }
        this.results.set(updated);
        this.submitting.set(false);
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(this.errorService.getMessage(err));
      },
    });
  }

  next(): void {
    if (this.currentIndex() + 1 >= this.appState.quizQuestions().length) {
      this.finish();
      return;
    }
    this.currentIndex.set(this.currentIndex() + 1);
    this.selectedOption.set(null);
    this.feedback.set(null);
  }

  private finish(): void {
    this.router.navigate(['/results']);
  }
}
