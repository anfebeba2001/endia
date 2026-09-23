import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppStateService } from '../../services/app-state.service';
import { QuizService } from '../../services/quiz.service';
import { ErrorService } from '../../services/error.service';
import { MeaningSnackService } from '../../services/meaning-snack.service';
import { QuizSummaryResponse } from '../../models/quiz.model';
import { SnackKeyword } from '../../models/snack.model';
import { MeaningSnack } from '../../models/meaning-snack.model';
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

  /** Feature 014: the keyword whose generation is in flight, and the user-safe error (if any). */
  readonly generatingKeyword = signal<SnackKeyword | null>(null);
  readonly generateError = signal<string | null>(null);

  /** Feature 014: the recap subtopic is the generation source (FR-001). */
  readonly recapSubtopicId = computed(() => this.summary()?.recap?.subtopicId ?? null);

  /** Feature 014: action available only when both theme and source subtopic are resolvable. */
  readonly generateEnabled = computed(
    () => !!this.appState.theme()?.themeId && !!this.recapSubtopicId(),
  );

  /** Feature 014: card state — loading, done (already generated), or idle (FR-003/FR-005/FR-006). */
  readonly generateState = computed<'idle' | 'loading' | 'done'>(() => {
    const keyword = this.activeKeyword();
    if (!keyword) {
      return 'idle';
    }
    if (this.generatingKeyword() === keyword) {
      return 'loading';
    }
    const subtopicId = this.recapSubtopicId();
    if (subtopicId && this.meaningSnackService.hasGenerated(subtopicId, keyword.term)) {
      return 'done';
    }
    return 'idle';
  });

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
    private meaningSnackService: MeaningSnackService,
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
    this.generateError.set(null);
  }

  /** Feature 013: clear the active keyword after any dismissal. */
  closeKeyword(): void {
    this.activeKeyword.set(null);
    this.activeKeywordAnchor.set(null);
    this.generateError.set(null);
  }

  /**
   * Feature 014: generate a meaning snack from the recap keyword. At most one in-flight request per
   * keyword; on failure the card shows the error and adds nothing (FR-001/FR-004/FR-005).
   */
  onGenerateRequested(keyword: SnackKeyword): void {
    const themeId = this.appState.theme()?.themeId;
    const subtopicId = this.recapSubtopicId();
    if (!themeId || !subtopicId) {
      this.generateError.set('This keyword cannot be generated right now.');
      return;
    }
    if (this.generatingKeyword() === keyword) {
      return;
    }
    this.generateError.set(null);
    this.generatingKeyword.set(keyword);
    this.meaningSnackService
      .generate(themeId, subtopicId, keyword.term, keyword.definition)
      .subscribe({
        next: (res) => {
          this.meaningSnackService.markGenerated(res.meaningSnack);
          this.appendMeaningSnack(res.meaningSnack);
          this.generatingKeyword.set(null);
        },
        error: (err) => {
          this.generatingKeyword.set(null);
          this.generateError.set(this.errorService.getMessage(err));
        },
      });
  }

  private appendMeaningSnack(snack: MeaningSnack): void {
    this.appState.meaningSnacks.update((list) =>
      list.some((m) => m.id === snack.id) ? list : [...list, snack],
    );
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
