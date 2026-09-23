import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ThemeService } from '../../services/theme.service';
import { ErrorService } from '../../services/error.service';
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

  constructor(
    private themeService: ThemeService,
    private errorService: ErrorService,
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
