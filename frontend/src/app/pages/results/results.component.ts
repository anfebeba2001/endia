import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppStateService } from '../../services/app-state.service';
import { QuizService } from '../../services/quiz.service';
import { ErrorService } from '../../services/error.service';
import { QuizSummaryResponse } from '../../models/quiz.model';
import { SnackKeyword } from '../../models/snack.model';
import { RichTextComponent } from '../../components/rich-text/rich-text.component';
import { KeywordDefinitionCardComponent } from '../../components/keyword-definition-card/keyword-definition-card.component';

@Component({
  selector: 'app-results',
  standalone: true,
  imports: [RichTextComponent, KeywordDefinitionCardComponent],
  templateUrl: './results.component.html',
  styleUrl: './results.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResultsComponent {
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly summary = signal<QuizSummaryResponse | null>(null);
  /** Feature 013: single active keyword/anchor pair → exactly one card per page (FR-018/FR-021). */
  readonly activeKeyword = signal<SnackKeyword | null>(null);
  readonly activeKeywordAnchor = signal<HTMLElement | null>(null);

  readonly perfect = computed(() => {
    const s = this.summary();
    return !!s && s.correctAnswers === s.totalQuestions && s.totalQuestions > 0;
  });

  readonly accuracy = computed(() => {
    const s = this.summary();
    if (!s || s.totalQuestions === 0) {
      return 0;
    }
    return Math.round((s.correctAnswers / s.totalQuestions) * 100);
  });

  constructor(
    private appState: AppStateService,
    private quizService: QuizService,
    private errorService: ErrorService,
    private router: Router,
  ) {
    void this.loadSummary();
  }

  private async loadSummary(): Promise<void> {
    const subtopicId = this.appState.quizSubTopicId();
    if (!subtopicId) {
      this.router.navigate(['/snacks']);
      return;
    }

    this.quizService.summary(subtopicId).subscribe({
      next: (s) => {
        this.summary.set(s);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(this.errorService.getMessage(err));
      },
    });
  }

  /** Feature 013: open the definition card for the activated term (replaces any previous one). */
  openKeyword(event: { keyword: SnackKeyword; anchor: HTMLElement }): void {
    this.activeKeyword.set(event.keyword);
    this.activeKeywordAnchor.set(event.anchor);
  }

  /** Feature 013: clear the active keyword after any dismissal. */
  closeKeyword(): void {
    this.activeKeyword.set(null);
    this.activeKeywordAnchor.set(null);
  }

  restart(): void {
    this.appState.reset();
    this.router.navigate(['/']);
  }

  /**
   * FR-001..FR-003/FR-018: return to the generated snacks, preserving state and activating the
   * quizzed subtopic's snack. Never calls `reset()` and issues no HTTP request.
   */
  backToSnacks(): void {
    const subtopicId = this.appState.selectedSubtopic()?.id ?? this.appState.quizSubTopicId();
    const idx = this.appState.snackIndexForSubtopic(subtopicId);
    if (idx >= 0) {
      this.appState.setSnackIndex(idx);
    }
    // No match (FR-018): keep the last/current index rather than jumping arbitrarily.
    this.router.navigate(['/snacks']);
  }
}
