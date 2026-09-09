import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppStateService } from '../../services/app-state.service';
import { SnackService } from '../../services/snack.service';
import { ErrorService } from '../../services/error.service';
import { Snack } from '../../models/snack.model';
import { Subtopic } from '../../models/theme.model';

@Component({
  selector: 'app-snacks',
  standalone: true,
  templateUrl: './snacks.component.html',
  styleUrl: './snacks.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SnacksComponent {
  readonly loading = signal(false);
  readonly generating = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly loadingSubtopic = signal<string | null>(null);

  constructor(
    private appState: AppStateService,
    private snackService: SnackService,
    private errorService: ErrorService,
    private router: Router
  ) {
    void this.loadSnacks();
  }

  get snacks(): Snack[] {
    return this.appState.snacks();
  }

  get subtopics(): Subtopic[] {
    return this.appState.subtopics();
  }

  get themeId(): string | null {
    return this.appState.theme()?.themeId ?? null;
  }

  get hasSnacks(): boolean {
    return this.snacks.length > 0;
  }

  private async loadSnacks(): Promise<void> {
    if (this.appState.snacks().length > 0) {
      return;
    }
    const themeId = this.themeId;
    const subIds = this.subtopics.map((s) => s.id);
    if (!themeId || subIds.length === 0) {
      this.router.navigate(['/subtopics']);
      return;
    }

    this.generating.set(true);
    this.snackService.generate(themeId, subIds).subscribe({
      next: (response) => {
        this.appState.snacks.set(response.snacks);
        this.generating.set(false);
      },
      error: (err) => {
        this.generating.set(false);
        this.errorMessage.set(this.errorService.getMessage(err));
      },
    });
  }

  startQuiz(subtopicId: string): void {
    const subtopic = this.subtopics.find((s) => s.id === subtopicId);
    if (!subtopic) {
      return;
    }
    this.appState.selectedSubtopic.set(subtopic);
    this.loadingSubtopic.set(subtopicId);
    this.router.navigate(['/quiz']);
  }

  goBack(): void {
    this.router.navigate(['/subtopics']);
  }
}
