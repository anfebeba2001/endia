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
import { RichTextComponent } from '../../components/rich-text/rich-text.component';

@Component({
  selector: 'app-snacks',
  standalone: true,
  imports: [RichTextComponent],
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
  /** Per-target terminal error message shown after the automatic budget is exhausted (FR-005). */
  readonly terminalErrors = signal<Record<string, string>>({});
  private readonly destroy$ = new Subject<void>();
  /** Which batch currently owns each target; prevents stale polls from double-triggering (FR-007). */
  private readonly ownerBatchId = new Map<string, string>();

  /**
   * FR-013/FR-012: the active position is the shared `AppStateService.currentSnackIndex`,
   * clamped so a stale or out-of-range value never renders a blank card.
   */
  readonly currentIndex = computed(() => {
    const len = this.items().length;
    if (len === 0) {
      return 0;
    }
    return Math.min(Math.max(this.appState.currentSnackIndex(), 0), len - 1);
  });
  readonly currentSnack = computed<BatchItem | null>(
    () => this.items()[this.currentIndex()] ?? null,
  );
  readonly nextSnack = computed<BatchItem | null>(
    () => this.items()[this.currentIndex() + 1] ?? null,
  );
  /** FR-015: Previous is disabled only at the first position. */
  readonly hasPrev = computed(() => this.currentIndex() > 0);
  /** FR-012: Next is unavailable at the last position. */
  readonly hasNext = computed(() => this.currentIndex() < this.items().length - 1);
  /** FR-016: Next only advances onto a READY snack (progressive delivery preserved). */
  readonly canNext = computed(() => this.hasNext() && this.nextSnack()?.snackStatus === 'READY');
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
    const existing = this.appState.snacks();
    if (existing.length > 0) {
      // FR-002/FR-004: return path — reuse already-generated snacks. No POST /api/batches,
      // no polling. Hydrate the view model first, then clamp the stored index (ordering matters:
      // clamping before hydration could reset the index to 0).
      this.items.set(existing.map(toReadyBatchItem));
      this.creating.set(false);
      this.polling.set(false);
      this.clampStoredIndex();
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
    this.clampStoredIndex();
  }

  /**
   * FR-012/SC-005: keep the shared index inside the current item range so a stale value never
   * renders a blank card. Called after hydration and whenever the item set changes.
   */
  private clampStoredIndex(): void {
    const len = this.items().length;
    if (len === 0) {
      return;
    }
    const clamped = Math.min(Math.max(this.appState.currentSnackIndex(), 0), len - 1);
    if (clamped !== this.appState.currentSnackIndex()) {
      this.appState.setSnackIndex(clamped);
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

  /** FR-012: reject out-of-range targets so rapid clicks cannot push the index out of range. */
  private goTo(index: number): void {
    const len = this.items().length;
    if (index < 0 || index >= len) {
      return;
    }
    this.appState.setSnackIndex(index);
  }

  /** FR-015: move back exactly one; Previous is disabled only at index 0. */
  prev(): void {
    if (this.hasPrev()) {
      this.goTo(this.currentIndex() - 1);
    }
  }

  /** FR-016: advance to the next snack only when it is READY (progressive delivery preserved). */
  next(): void {
    if (this.canNext()) {
      this.goTo(this.currentIndex() + 1);
    }
  }

  /** FR-009/FR-010: jump to a READY entry (guarded even though the button is disabled). */
  selectIndex(i: number): void {
    if (this.items()[i]?.snackStatus === 'READY') {
      this.goTo(i);
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

  /** FR-017: the quiz targets the active snack's subtopic, never a stale/first one. */
  startQuiz(): void {
    const subtopicId =
      this.currentSnack()?.snack?.subtopicId ?? this.currentSnack()?.subtopicId;
    if (!subtopicId) {
      return;
    }
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

/**
 * Return-path hydration (FR-004): map an already-generated `Snack` to a READY `BatchItem`,
 * preserving `subtopicId`/`subtopicName`/`snack` for the card, the index, and "Start Quiz".
 */
function toReadyBatchItem(s: Snack): BatchItem {
  return {
    subtopicId: s.subtopicId,
    subtopicName: s.subtopicName,
    snackStatus: 'READY',
    snack: s,
    snackCause: null,
    quizStatus: 'READY',
    quizCause: null,
  };
}
