import { ChangeDetectionStrategy, Component, OnDestroy, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppStateService } from '../../services/app-state.service';
import { BatchPollingTimeoutError, BatchService } from '../../services/batch.service';
import { SnackRetryService } from '../../services/snack-retry.service';
import { ErrorService } from '../../services/error.service';
import { Snack } from '../../models/snack.model';
import { Subtopic } from '../../models/theme.model';
import {
  BatchItem,
  BatchStatusResponse,
  SnackGenerationPhase,
  phaseMessage as phaseToMessage,
} from '../../models/batch.model';
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
  /** True from create/retry success until the first terminal status or poll error (FR-009/FR-010). */
  readonly polling = signal(false);
  readonly loadingSubtopic = signal<string | null>(null);
  readonly currentIndex = signal(0);
  /** Per-target terminal error message shown after the automatic budget is exhausted (FR-005). */
  readonly terminalErrors = signal<Record<string, string>>({});
  private readonly destroy$ = new Subject<void>();
  /** Which batch currently owns each target; prevents stale polls from double-triggering (FR-007). */
  private readonly ownerBatchId = new Map<string, string>();

  readonly currentSnack = computed<BatchItem | null>(
    () => this.items()[this.currentIndex()] ?? null,
  );
  readonly nextSnack = computed<BatchItem | null>(
    () => this.items()[this.currentIndex() + 1] ?? null,
  );
  readonly anyReady = computed(() => this.items().some((i) => i.snackStatus === 'READY'));

  /**
   * FR-009/FR-010/FR-011: the empty state is reserved for a genuinely terminal batch that
   * produced no ready snacks. It must never render during creation, before the first poll
   * response, or while the batch is still IN_PROGRESS.
   */
  readonly showEmptyState = computed(
    () =>
      !this.creating() &&
      !this.polling() &&
      this.items().length === 0 &&
      this.batchStatus() !== 'IN_PROGRESS' &&
      !this.errorMessage(),
  );

  constructor(
    private appState: AppStateService,
    private batchService: BatchService,
    private retryService: SnackRetryService,
    private errorService: ErrorService,
    private router: Router,
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

    // FR-001/FR-007: a brand-new batch starts a fresh budget and clears any prior ownership.
    this.retryService.resetAll();
    this.ownerBatchId.clear();

    this.batchService.createBatch(themeId, subIds).subscribe({
      next: (created) => {
        this.batchId.set(created.batchId);
        this.appState.setBatch(created.batchId);
        this.creating.set(false);
        // FR-009/FR-010: keep the progress state visible until the first terminal poll response.
        this.polling.set(true);
        for (const id of subIds) {
          this.ownerBatchId.set(id, created.batchId);
          this.retryService.registerAttempt(id);
        }
        this.poll(created.batchId);
      },
      error: (err) => {
        this.creating.set(false);
        this.polling.set(false);
        this.errorMessage.set(this.errorService.getMessage(err));
      },
    });
  }

  private poll(batchId: string): void {
    this.batchService
      .pollStatus(batchId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (status) => this.applyStatus(status, batchId),
        error: (err) => this.handlePollError(err),
      });
  }

  private applyStatus(status: BatchStatusResponse, batchId: string): void {
    this.batchStatus.set(status.status);

    const settled: string[] = [];
    for (const item of status.items) {
      if (!this.ownsItem(item.subtopicId, batchId)) {
        continue;
      }
      this.mergeItem(item);
      if (item.snackStatus === 'READY' || item.snackStatus === 'FAILED') {
        settled.push(item.subtopicId);
      }
    }

    // A settled in-flight retry cycle releases its guard; a READY result clears the terminal error.
    for (const target of settled) {
      if (this.retryService.isInFlight(target)) {
        this.retryService.clearInFlight(target);
        if (this.itemFor(target)?.snackStatus === 'READY') {
          this.clearTerminalError(target);
        }
      }
    }

    this.retrying.set(status.retrying || this.retryService.hasInFlight());
    this.syncAppStateSnacks();
    this.capturePrefetchedQuiz(status.items);
    this.maybeRetryFailed(status.items, batchId);
    // FR-011/FR-013: a terminal status ends the cycle unless a fresh retry cycle is already in flight.
    if (status.status !== 'IN_PROGRESS' && !this.retryService.hasInFlight()) {
      this.polling.set(false);
    }
  }

  private maybeRetryFailed(items: BatchItem[], batchId: string): void {
    for (const item of items) {
      if (item.snackStatus !== 'FAILED') {
        continue;
      }
      const target = item.subtopicId;
      if (!this.ownsItem(target, batchId)) {
        continue;
      }
      if (this.retryService.canRetry(target)) {
        // FR-003/FR-004: an explicit failure is the only trigger for an automatic reattempt.
        this.retryTarget(target);
      } else if (!this.retryService.isInFlight(target)) {
        // FR-005: budget exhausted → terminal, user-visible error naming the typed cause.
        this.setTerminalError(target, item.snackCause);
      }
    }
  }

  /**
   * Start exactly one new generation cycle for a target (FR-006/FR-007). Shared by the automatic
   * reattempt path and the manual retry affordance so the counter/guard are single-sourced.
   */
  private retryTarget(target: string): void {
    const themeId = this.themeId;
    if (!themeId) {
      return;
    }
    if (!this.retryService.canRetry(target)) {
      if (!this.retryService.isInFlight(target)) {
        this.setTerminalError(target, this.itemFor(target)?.snackCause ?? null);
      }
      return;
    }

    this.retryService.registerAttempt(target);
    this.retryService.markInFlight(target);
    this.clearTerminalError(target);
    this.retrying.set(true);
    // FR-009/FR-014: a retry cycle is active generation, so keep progress (and suppress the empty state).
    this.polling.set(true);

    // FR-003: a single-target batch — never re-create the whole batch or regenerate healthy targets.
    this.batchService.createBatch(themeId, [target]).subscribe({
      next: (created) => {
        this.ownerBatchId.set(target, created.batchId);
        this.poll(created.batchId);
      },
      error: (err) => {
        // A failed create request is itself an explicit error; it consumes one attempt (FR-003).
        this.retryService.clearInFlight(target);
        this.retrying.set(this.retryService.hasInFlight());
        if (this.retryService.attemptsFor(target) >= 3) {
          this.polling.set(false);
          this.setTerminalError(target, null, this.errorService.getMessage(err));
        } else {
          this.retryTarget(target);
        }
      },
    });
  }

  private handlePollError(err: unknown): void {
    // FR-013/FR-014: any poll error (including the bounded-window timeout) ends the progress cycle.
    this.polling.set(false);
    if (err instanceof BatchPollingTimeoutError) {
      // FR-010/SC-004: bounded lifetime elapsed — terminal, user-visible error, progress stopped.
      this.creating.set(false);
      this.retrying.set(false);
      this.batchStatus.set('FAILED');
      this.errorMessage.set(err.message);
      return;
    }
    this.errorMessage.set(this.errorService.getMessage(err));
  }

  private ownsItem(target: string, batchId: string): boolean {
    const owner = this.ownerBatchId.get(target);
    return !owner || owner === batchId;
  }

  private mergeItem(item: BatchItem): void {
    const current = this.items();
    const idx = current.findIndex((i) => i.subtopicId === item.subtopicId);
    if (idx >= 0) {
      const next = [...current];
      next[idx] = item;
      this.items.set(next);
    } else {
      this.items.set([...current, item]);
    }
  }

  private itemFor(target: string): BatchItem | undefined {
    return this.items().find((i) => i.subtopicId === target);
  }

  private syncAppStateSnacks(): void {
    const snacks: Snack[] = this.items()
      .filter((i) => i.snackStatus === 'READY' && i.snack)
      .map((i) => i.snack as Snack);
    this.appState.snacks.set(snacks);
  }

  private capturePrefetchedQuiz(items: BatchItem[]): void {
    for (const item of items) {
      if (item.quizStatus === 'READY' && item.quizQuestions?.length) {
        this.appState.setQuizPrefetch(item.subtopicId, item.quizQuestions);
      }
    }
  }

  private setTerminalError(target: string, cause: string | null, custom?: string): void {
    const reason = custom ?? (cause ? `Cause: ${cause}` : 'Cause: Unknown');
    this.terminalErrors.update((m) => ({ ...m, [target]: reason }));
    this.retrying.set(this.retryService.hasInFlight());
  }

  private clearTerminalError(target: string): void {
    this.terminalErrors.update((m) => {
      if (!(target in m)) {
        return m;
      }
      const next = { ...m };
      delete next[target];
      return next;
    });
  }

  /** Terminal error for a target, if its automatic budget was exhausted (FR-005). */
  terminalError(subtopicId: string): string | null {
    return this.terminalErrors()[subtopicId] ?? null;
  }

  /** Whether a generation cycle is currently in flight for the target (FR-007). */
  isInFlight(subtopicId: string): boolean {
    return this.retryService.isInFlight(subtopicId);
  }

  /** Feature 008: map the item's reported phase to its static waiting message (FR-005/FR-006). */
  phaseMessage(phase: SnackGenerationPhase | null | undefined): string {
    return phaseToMessage(phase);
  }

  /** Advance to the next snack only when it is READY (FR-002). */
  next(): void {
    if (this.nextSnack()?.snackStatus === 'READY') {
      this.currentIndex.set(this.currentIndex() + 1);
    }
  }

  /** Manual retry: resets the target's budget and starts exactly one new cycle (FR-006). */
  retry(subtopicId: string): void {
    if (this.retryService.isInFlight(subtopicId)) {
      return;
    }
    this.retryService.resetBudget(subtopicId);
    this.clearTerminalError(subtopicId);
    this.errorMessage.set(null);
    this.retryTarget(subtopicId);
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
