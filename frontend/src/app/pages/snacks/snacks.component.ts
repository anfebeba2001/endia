import { ChangeDetectionStrategy, Component, OnDestroy, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppStateService } from '../../services/app-state.service';
import { BatchService } from '../../services/batch.service';
import { ErrorService } from '../../services/error.service';
import { Snack } from '../../models/snack.model';
import { Subtopic } from '../../models/theme.model';
import { BatchItem, BatchStatusResponse } from '../../models/batch.model';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-snacks',
  standalone: true,
  templateUrl: './snacks.component.html',
  styleUrl: './snacks.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SnacksComponent implements OnDestroy {
  readonly items = signal<BatchItem[]>([]);
  readonly batchStatus = signal<string>('IN_PROGRESS');
  readonly retrying = signal(false);
  readonly batchId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly creating = signal(true);
  readonly loadingSubtopic = signal<string | null>(null);
  readonly currentIndex = signal(0);
  private readonly destroy$ = new Subject<void>();

  readonly currentSnack = computed<BatchItem | null>(() => this.items()[this.currentIndex()] ?? null);
  readonly nextSnack = computed<BatchItem | null>(() => this.items()[this.currentIndex() + 1] ?? null);
  readonly anyReady = computed(() => this.items().some((i) => i.snackStatus === 'READY'));

  constructor(
    private appState: AppStateService,
    private batchService: BatchService,
    private errorService: ErrorService,
    private router: Router
  ) {
    void this.init();
  }

  get subtopics(): Subtopic[] {
    return this.appState.subtopics();
  }

  get themeId(): string | null {
    return this.appState.theme()?.themeId ?? null;
  }

  private async init(): Promise<void> {
    if (this.appState.snacks().length > 0) {
      return;
    }
    const themeId = this.themeId;
    const subIds = this.subtopics.map((s) => s.id);
    if (!themeId || subIds.length === 0) {
      this.router.navigate(['/subtopics']);
      return;
    }

    this.batchService.createBatch(themeId, subIds).subscribe({
      next: (created) => {
        this.batchId.set(created.batchId);
        this.appState.setBatch(created.batchId);
        this.creating.set(false);
        this.poll(created.batchId);
      },
      error: (err) => {
        this.creating.set(false);
        this.errorMessage.set(this.errorService.getMessage(err));
      },
    });
  }

  private poll(batchId: string): void {
    this.batchService.pollStatus(batchId).pipe(takeUntil(this.destroy$)).subscribe({
      next: (status) => this.applyStatus(status),
      error: (err) => this.errorMessage.set(this.errorService.getMessage(err)),
    });
  }

  private applyStatus(status: BatchStatusResponse): void {
    this.items.set(status.items);
    this.batchStatus.set(status.status);
    this.retrying.set(status.retrying);
    // Populate appState snacks for the flow that reads them below the progressive queue.
    const snacks: Snack[] = status.items
      .filter((i) => i.snackStatus === 'READY' && i.snack)
      .map((i) => i.snack as Snack);
    this.appState.snacks.set(snacks);

    // FR-003/SC-002: capture the prefetched quiz so "Start Quiz" consumes it without
    // a second /api/quiz/generate call.
    for (const item of status.items) {
      if (item.quizStatus === 'READY' && item.quizQuestions?.length) {
        this.appState.setQuizPrefetch(item.subtopicId, item.quizQuestions);
      }
    }
  }

  /** Advance to the next snack only when it is READY (FR-002). */
  next(): void {
    if (this.nextSnack()?.snackStatus === 'READY') {
      this.currentIndex.set(this.currentIndex() + 1);
    }
  }

  /** Retry a failed snack (FR-004/FR-017). */
  retry(subtopicId: string): void {
    const batchId = this.batchId();
    if (!batchId) {
      return;
    }
    // Re-poll to surface a fresh batch status; retry is driven by a fresh createBatch for the failed item.
    this.resetFailed(subtopicId);
  }

  private resetFailed(_subtopicId: string): void {
    // For MVP, a retry simply re-creates the batch for all selected subtopics.
    const themeId = this.themeId;
    const subIds = this.subtopics.map((s) => s.id);
    if (!themeId || subIds.length === 0) {
      return;
    }
    this.items.set([]);
    this.currentIndex.set(0);
    this.creating.set(true);
    this.batchService.createBatch(themeId, subIds).subscribe({
      next: (created) => {
        this.batchId.set(created.batchId);
        this.appState.setBatch(created.batchId);
        this.creating.set(false);
        this.poll(created.batchId);
      },
      error: (err) => this.errorMessage.set(this.errorService.getMessage(err)),
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

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
