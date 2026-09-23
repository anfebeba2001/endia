import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { ThemeService } from '../../services/theme.service';
import { ErrorService } from '../../services/error.service';
import { MeaningSnackService } from '../../services/meaning-snack.service';
import { ThemeHistory } from '../../models/history.model';
import { SnackKeyword } from '../../models/snack.model';
import { RichTextComponent } from '../../components/rich-text/rich-text.component';
import { KeywordDefinitionCardComponent } from '../../components/keyword-definition-card/keyword-definition-card.component';

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [RichTextComponent, KeywordDefinitionCardComponent],
  templateUrl: './history.component.html',
  styleUrl: './history.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoryComponent {
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly themes = signal<ThemeHistory[]>([]);
  readonly expanded = signal<ReadonlySet<string>>(new Set<string>());
  /** Feature 013: single active keyword/anchor pair → exactly one card per page (FR-018/FR-021). */
  readonly activeKeyword = signal<SnackKeyword | null>(null);
  readonly activeKeywordAnchor = signal<HTMLElement | null>(null);

  /** Feature 014: the theme/subtopic the active keyword belongs to (generation source). */
  readonly activeThemeId = signal<string | null>(null);
  readonly activeSubtopicId = signal<string | null>(null);
  readonly generatingKeyword = signal<SnackKeyword | null>(null);
  readonly generateError = signal<string | null>(null);

  /** Feature 014: action available only when the active keyword has a theme + subtopic context. */
  readonly generateEnabled = computed(() => !!this.activeThemeId() && !!this.activeSubtopicId());

  /** Feature 014: card state — loading, done (already generated), or idle (FR-003/FR-005/FR-006). */
  readonly generateState = computed<'idle' | 'loading' | 'done'>(() => {
    const keyword = this.activeKeyword();
    if (!keyword) {
      return 'idle';
    }
    if (this.generatingKeyword() === keyword) {
      return 'loading';
    }
    const subtopicId = this.activeSubtopicId();
    if (subtopicId && this.meaningSnackService.hasGenerated(subtopicId, keyword.term)) {
      return 'done';
    }
    return 'idle';
  });

  constructor(
    private themeService: ThemeService,
    private errorService: ErrorService,
    private meaningSnackService: MeaningSnackService,
  ) {
    this.themeService.listHistory().subscribe({
      next: (themes) => {
        this.themes.set(themes);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(this.errorService.getMessage(err));
      },
    });
  }

  isExpanded(themeId: string): boolean {
    return this.expanded().has(themeId);
  }

  toggle(themeId: string): void {
    this.expanded.update((current) => {
      const next = new Set(current);
      if (next.has(themeId)) {
        next.delete(themeId);
      } else {
        next.add(themeId);
      }
      return next;
    });
  }

  /**
   * Feature 013/014: open the definition card for the activated term, capturing the theme/subtopic
   * context so the "Generate snack" action can resolve its source (FR-001).
   */
  openKeyword(
    event: { keyword: SnackKeyword; anchor: HTMLElement },
    themeId: string,
    subtopicId: string,
  ): void {
    this.activeKeyword.set(event.keyword);
    this.activeKeywordAnchor.set(event.anchor);
    this.activeThemeId.set(themeId);
    this.activeSubtopicId.set(subtopicId);
    this.generateError.set(null);
  }

  /** Feature 013: clear the active keyword after any dismissal. */
  closeKeyword(): void {
    this.activeKeyword.set(null);
    this.activeKeywordAnchor.set(null);
    this.activeThemeId.set(null);
    this.activeSubtopicId.set(null);
    this.generateError.set(null);
  }

  /**
   * Feature 014: generate a meaning snack from the active keyword. At most one in-flight request per
   * keyword; on failure the card shows the error and adds nothing (FR-001/FR-004/FR-005).
   */
  onGenerateRequested(keyword: SnackKeyword): void {
    const themeId = this.activeThemeId();
    const subtopicId = this.activeSubtopicId();
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
          this.generatingKeyword.set(null);
        },
        error: (err) => {
          this.generatingKeyword.set(null);
          this.generateError.set(this.errorService.getMessage(err));
        },
      });
  }

  formatDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(date);
  }
}
