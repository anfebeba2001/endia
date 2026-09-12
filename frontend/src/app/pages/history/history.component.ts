import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ThemeService } from '../../services/theme.service';
import { ErrorService } from '../../services/error.service';
import { ThemeHistory } from '../../models/history.model';

@Component({
  selector: 'app-history',
  standalone: true,
  templateUrl: './history.component.html',
  styleUrl: './history.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoryComponent {
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly themes = signal<ThemeHistory[]>([]);
  readonly expanded = signal<ReadonlySet<string>>(new Set<string>());

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
