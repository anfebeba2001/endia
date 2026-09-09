import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppStateService } from '../../services/app-state.service';
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

  constructor(private appState: AppStateService, private router: Router) {
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

  goToSnacks(): void {
    const ids = Array.from(this.selectedIds());
    this.appState.setSelectedSubtopics(ids);
    this.router.navigate(['/snacks']);
  }

  goBack(): void {
    this.router.navigate(['/']);
  }
}
