import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppStateService } from '../../services/app-state.service';
import { ThemeService } from '../../services/theme.service';
import { ErrorService } from '../../services/error.service';
import { Subtopic } from '../../models/theme.model';

@Component({
  selector: 'app-subtopics',
  standalone: true,
  templateUrl: './subtopics.component.html',
  styleUrl: './subtopics.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SubtopicsComponent {
  readonly selectedIds = signal<Set<string>>(new Set());
  readonly customName = signal('');
  readonly adding = signal(false);
  readonly addFeedback = signal<string | null>(null);

  constructor(
    private appState: AppStateService,
    private themeService: ThemeService,
    private errorService: ErrorService,
    private router: Router
  ) {
    // Pre-select all subtopics by default.
    const all = this.appState.subtopics().map((s) => s.id);
    this.selectedIds.set(new Set(all));
  }

  get subtopics(): Subtopic[] {
    return this.appState.subtopics();
  }

  get themeId(): string | null {
    return this.appState.theme()?.themeId ?? null;
  }

  get hasSelection(): boolean {
    return this.selectedIds().size > 0;
  }

  toggle(subtopicId: string): void {
    const next = new Set(this.selectedIds());
    if (next.has(subtopicId)) {
      next.delete(subtopicId);
    } else {
      next.add(subtopicId);
    }
    this.selectedIds.set(next);
  }

  isSelected(subtopicId: string): boolean {
    return this.selectedIds().has(subtopicId);
  }

  addCustom(): void {
    const name = this.customName().trim();
    const themeId = this.themeId;
    if (!name || !themeId || this.adding()) {
      return;
    }
    this.adding.set(true);
    this.addFeedback.set(null);
    this.themeService.addSubtopics(themeId, [name]).subscribe({
      next: (resp) => {
        // Merge added subtopics into the current list and auto-select them.
        const merged = [...this.subtopics, ...resp.added];
        this.appState.subtopics.set(merged);
        const next = new Set(this.selectedIds());
        resp.added.forEach((a) => next.add(a.id));
        this.selectedIds.set(next);
        this.adding.set(false);
        this.customName.set('');
        if (resp.duplicates.length > 0) {
          this.addFeedback.set(`"${resp.duplicates.join('", "')}" already covered.`);
        } else {
          this.addFeedback.set(`Added "${resp.added.map((a) => a.name).join('", "')}".`);
        }
      },
      error: (err) => {
        this.adding.set(false);
        this.addFeedback.set(this.errorService.getMessage(err));
      },
    });
  }

  goToSnacks(): void {
    const ids = Array.from(this.selectedIds());
    this.appState.setSelectedSubtopics(ids);
    this.router.navigate(['/snacks']);
  }

  goBack(): void {
    this.router.navigate(['/']);
  }
}
