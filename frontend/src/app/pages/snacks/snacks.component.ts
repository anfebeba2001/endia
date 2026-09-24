import { ChangeDetectionStrategy, Component, OnDestroy, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppStateService } from '../../services/app-state.service';
import { BatchPollingTimeoutError, BatchService } from '../../services/batch.service';
import { SnackRetryService } from '../../services/snack-retry.service';
import { ErrorService } from '../../services/error.service';
import { MeaningSnackService } from '../../services/meaning-snack.service';
import { Snack, SnackKeyword } from '../../models/snack.model';
import { MeaningSnack } from '../../models/meaning-snack.model';
import { Subtopic } from '../../models/theme.model';
import {
  BackboneEntry,
  BatchItem,
  BatchStatusResponse,
  MeaningEntry,
  RegularEntry,
  SnackGenerationPhase,
  phaseMessage as phaseToMessage,
} from '../../models/batch.model';
import { Subject, takeUntil } from 'rxjs';
import { RichTextComponent } from '../../components/rich-text/rich-text.component';
import { KeywordDefinitionCardComponent } from '../../components/keyword-definition-card/keyword-definition-card.component';
import { SelectionDefineDirective } from '../../components/selection-define/selection-define.directive';
import { SelectionDefineAffordanceComponent } from '../../components/selection-define/selection-define-affordance.component';
import { DefinitionService } from '../../services/definition.service';

type GenerateState = 'idle' | 'loading' | 'done';

/** Feature 016: maximum whitespace-separated tokens accepted for a selected term (FR-010). */
const MAX_TERM_TOKENS = 10;

@Component({
  selector: 'app-snacks',
  standalone: true,
  imports: [
    RichTextComponent,
    KeywordDefinitionCardComponent,
    SelectionDefineDirective,
    SelectionDefineAffordanceComponent,
  ],
  templateUrl: './snacks.component.html',
  styleUrl: './snacks.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SnacksComponent implements OnDestroy {
  /**
   * Regular batch items only. Every existing batch/poll/retry/progressive-delivery path operates
   * exclusively on this signal; the meaning-snack interleave is a pure projection over it.
   */
  readonly batchItems = signal<BatchItem[]>([]);
  /** Feature 014: persisted meaning snacks for the theme (backend listing is the source of truth). */
  readonly meaningSnacks = signal<MeaningSnack[]>([]);
  /** Feature 014: per-keyword generation state for the definition card. */
  readonly generateStates = signal<Record<string, GenerateState>>({});
  /** Feature 014: user-safe inline error for the last failed generation (FR-004). */
  readonly generateError = signal<string | null>(null);
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
  /** Feature 013: the single active keyword/anchor pair; exactly one card per page (FR-018/FR-021). */
  readonly activeKeyword = signal<SnackKeyword | null>(null);
  readonly activeKeywordAnchor = signal<HTMLElement | null>(null);

  // ─── Feature 016 — custom keyword "Define" (held selection) ────────────────
  /** The held selection awaiting activation, or null when no affordance is shown. */
  readonly selectionAffordance = signal<{ term: string; rect: DOMRect } | null>(null);
  /** A meaningful, user-safe error for an invalid selection (blank/oversized), or null. */
  readonly selectionError = signal<string | null>(null);
  /** True while a definition request is in flight; guards against duplicate requests (FR-007). */
  readonly defining = signal(false);
  /** Whether the open custom-definition card is loading (FR-006). */
  readonly definitionLoading = signal(false);
  /** A user-safe error for a failed custom-definition generation, or null (FR-009). */
  readonly definitionError = signal<string | null>(null);
  /** Whether the active card was opened by the custom-Define flow (no "Generate snack" action). */
  readonly customDefinition = signal(false);
  /** The selection rect used to position the custom-definition card. */
  readonly activeKeywordRect = signal<DOMRect | null>(null);
  /**
   * Feature 014: the regular subtopic the active keyword belongs to, captured at card-open time.
   * Generation uses this captured source (not the possibly-changed `currentEntry`) so keyboard
   * navigation can never attach a snack to the wrong subtopic.
   */
  private readonly activeKeywordSubtopicId = signal<string | null>(null);
  private readonly destroy$ = new Subject<void>();
  /** Which batch currently owns each target; prevents stale polls from double-triggering (FR-007). */
  private readonly ownerBatchId = new Map<string, string>();
  /** Feature 014: at most one in-flight generation per keyword key (FR-005). */
  private readonly generatingKeys = new Set<string>();

  /**
   * Feature 014: the pure interleave of regular batch items and meaning snacks. Each meaning snack
   * is placed immediately after its source subtopic's regular entry (in listing order); meaning
   * snacks whose source subtopic is not in the batch are appended at the end. `entryId` is stable
   * and collision-free (`subtopicId` vs `meaning:<id>`), so `@for` tracking and index clamping stay
   * correct across a mixed list (FR-010..FR-013, data-model.md §7).
   */
  readonly entries = computed<BackboneEntry[]>(() => {
    const regular = this.batchItems();
    const meanings = this.meaningSnacks();
    const placed = new Set<string>();
    const out: BackboneEntry[] = [];
    for (const item of regular) {
      out.push(toRegularEntry(item));
      for (const meaning of meanings) {
        if (meaning.sourceSubtopicId === item.subtopicId && !placed.has(meaning.id)) {
          out.push(toMeaningEntry(meaning));
          placed.add(meaning.id);
        }
      }
    }
    for (const meaning of meanings) {
      if (!placed.has(meaning.id)) {
        out.push(toMeaningEntry(meaning));
        placed.add(meaning.id);
      }
    }
    return out;
  });

  /**
   * FR-013/FR-012: the active position is the shared `AppStateService.currentSnackIndex`,
   * clamped so a stale or out-of-range value never renders a blank card.
   */
  readonly currentIndex = computed(() => {
    const len = this.entries().length;
    if (len === 0) {
      return 0;
    }
    return Math.min(Math.max(this.appState.currentSnackIndex(), 0), len - 1);
  });
  readonly currentEntry = computed<BackboneEntry | null>(
    () => this.entries()[this.currentIndex()] ?? null,
  );
  readonly nextEntry = computed<BackboneEntry | null>(
    () => this.entries()[this.currentIndex() + 1] ?? null,
  );
  /** FR-015: Previous is disabled only at the first position. */
  readonly hasPrev = computed(() => this.currentIndex() > 0);
  /** FR-012: Next is unavailable at the last position. */
  readonly hasNext = computed(() => this.currentIndex() < this.entries().length - 1);
  /**
   * FR-016: Next only advances onto a READY regular snack, or onto a meaning entry (always
   * readable) — progressive delivery of regular snacks is preserved (FR-011/FR-013).
   */
  readonly canNext = computed(() => {
    const next = this.nextEntry();
    return this.hasNext() && !!next && (next.kind === 'meaning' || next.snackStatus === 'READY');
  });
  readonly anyReady = computed(() => this.batchItems().some((i) => i.snackStatus === 'READY'));

  /**
   * Feature 014/017: whether the definition card offers the snack-creation action. For a regular
   * highlighted keyword this is the unchanged feature-014 predicate; for a custom-Define card
   * (feature 017) the action is offered only once a valid generated meaning exists and a source
   * subtopic was captured (FR-002). The action slot is relabeled, never duplicated (FR-003).
   */
  readonly generateEnabled = computed(() => {
    if (!this.themeId) {
      return false;
    }
    if (this.customDefinition()) {
      // Feature 017: no action while defining, on a definition error, on a blank meaning, or when
      // no source subtopic could be resolved (FR-002, SC-001).
      return (
        !this.definitionLoading() &&
        !this.definitionError() &&
        !!this.activeKeyword()?.definition &&
        !!this.activeKeywordSubtopicId()
      );
    }
    // Feature-014 regular keyword card: behavior-equivalent to before this feature.
    return this.currentEntry()?.kind === 'regular';
  });

  /**
   * Feature 014/017: the card state for the active keyword, keyed by the source subtopic captured
   * when the card opened (`openKeyword` for regular keywords, `onDefineRequested` for the custom
   * flow). The `hasGenerated` fallback gives the already-generated (`done`) state on re-open
   * (FR-003/FR-005/FR-006/FR-010).
   */
  readonly activeGenerateState = computed<GenerateState>(() => {
    const keyword = this.activeKeyword();
    const subtopicId = this.activeKeywordSubtopicId();
    if (!keyword || !subtopicId) {
      return 'idle';
    }
    const state = this.generateStates()[generateKey(subtopicId, keyword.term)];
    if (state) {
      return state;
    }
    return this.meaningSnackService.hasGenerated(subtopicId, keyword.term) ? 'done' : 'idle';
  });

  /**
   * FR-009/FR-010/FR-011: the empty state is reserved for a genuinely terminal batch that
   * produced no entries. It must never render during creation, before the first poll
   * response, while the batch is still IN_PROGRESS, or when a meaning snack is present.
   */
  readonly showEmptyState = computed(
    () =>
      !this.creating() &&
      !this.polling() &&
      this.entries().length === 0 &&
      this.batchStatus() !== 'IN_PROGRESS' &&
      !this.errorMessage(),
  );

  constructor(
    private appState: AppStateService,
    private batchService: BatchService,
    private retryService: SnackRetryService,
    private errorService: ErrorService,
    private meaningSnackService: MeaningSnackService,
    private definitionService: DefinitionService,
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
    // Feature 014: clear per-keyword generation state on (re)entry so it never leaks across themes.
    this.generateStates.set({});
    this.generatingKeys.clear();
    const existing = this.appState.snacks();
    if (existing.length > 0) {
      // FR-002/FR-004: return path — reuse already-generated snacks. No POST /api/batches,
      // no polling. Hydrate the view model first, then clamp the stored index (ordering matters:
      // clamping before hydration could reset the index to 0).
      this.batchItems.set(existing.map(toReadyBatchItem));
      this.creating.set(false);
      this.polling.set(false);
      const returnThemeId = this.themeId;
      if (returnThemeId) {
        this.loadMeaningSnacks(returnThemeId);
      }
      this.clampStoredIndex();
      return;
    }
    const themeId = this.themeId;
    const subIds = this.subtopics.map((s) => s.id);
    if (!themeId || subIds.length === 0) {
      this.router.navigate(['/subtopics']);
      return;
    }

    // FR-018/FR-020: the meaning-snack listing is fetched on every page load, independent of the
    // regular batch. A failure degrades to an empty list and never affects the batch flow.
    this.loadMeaningSnacks(themeId);

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

  /**
   * Feature 014: load persisted meaning snacks for the theme and cache them. Any error (including
   * 404/500/network) is treated as "no meaning snacks" so regular snacks render normally
   * (FR-018/FR-020, SC-006).
   */
  private loadMeaningSnacks(themeId: string): void {
    this.meaningSnackService.listByTheme(themeId).subscribe({
      next: (res) => {
        this.meaningSnacks.set(res.meaningSnacks);
        this.appState.meaningSnacks.set(res.meaningSnacks);
        for (const meaning of res.meaningSnacks) {
          this.meaningSnackService.markGenerated(meaning);
        }
        this.clampStoredIndex();
      },
      error: () => {
        this.meaningSnacks.set([]);
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
    const current = this.batchItems();
    const idx = current.findIndex((i) => i.subtopicId === item.subtopicId);
    if (idx >= 0) {
      const next = [...current];
      next[idx] = item;
      this.batchItems.set(next);
    } else {
      this.batchItems.set([...current, item]);
    }
    this.clampStoredIndex();
  }

  /**
   * FR-012/SC-005: keep the shared index inside the current entry range so a stale value never
   * renders a blank card. Called after hydration/listing and whenever the entry set changes.
   */
  private clampStoredIndex(): void {
    const len = this.entries().length;
    if (len === 0) {
      return;
    }
    const clamped = Math.min(Math.max(this.appState.currentSnackIndex(), 0), len - 1);
    if (clamped !== this.appState.currentSnackIndex()) {
      this.appState.setSnackIndex(clamped);
    }
  }

  private itemFor(target: string): BatchItem | undefined {
    return this.batchItems().find((i) => i.subtopicId === target);
  }

  /** FR-024: only regular snacks are stored in AppState (drives the regular quiz flow). */
  private syncAppStateSnacks(): void {
    const snacks: Snack[] = this.batchItems()
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
    const len = this.entries().length;
    if (index < 0 || index >= len) {
      return;
    }
    // Feature 014: changing the active entry dismisses any open definition card so a keyword can
    // never be generated against a different (newer) source subtopic.
    this.closeKeyword();
    // Feature 016: navigation also dismisses any pending selection affordance.
    this.selectionAffordance.set(null);
    this.selectionError.set(null);
    this.appState.setSnackIndex(index);
  }

  /** FR-015: move back exactly one; Previous is disabled only at index 0. */
  prev(): void {
    if (this.hasPrev()) {
      this.goTo(this.currentIndex() - 1);
    }
  }

  /** FR-016: advance to the next entry when it is a meaning entry or a READY regular snack. */
  next(): void {
    if (this.canNext()) {
      this.goTo(this.currentIndex() + 1);
    }
  }

  /**
   * FR-009/FR-010: jump to a selectable entry — a meaning entry is always selectable; a regular
   * entry only when READY (guarded even though the button is disabled).
   */
  selectIndex(i: number): void {
    const entry = this.entries()[i];
    if (entry && (entry.kind === 'meaning' || entry.snackStatus === 'READY')) {
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

  /**
   * FR-015/FR-017: the quiz targets the active regular snack's subtopic, never a stale/first one.
   * A meaning entry has no quiz and early-returns (FR-014/FR-015).
   */
  startQuiz(): void {
    const entry = this.currentEntry();
    if (!entry || entry.kind !== 'regular') {
      return;
    }
    const subtopicId = entry.snack?.subtopicId ?? entry.subtopicId;
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

  /** Feature 013: open the definition card for the activated term (replaces any previous one). */
  openKeyword(event: { keyword: SnackKeyword; anchor: HTMLElement }): void {
    const entry = this.currentEntry();
    this.activeKeywordSubtopicId.set(entry?.kind === 'regular' ? entry.subtopicId : null);
    this.activeKeyword.set(event.keyword);
    this.activeKeywordAnchor.set(event.anchor);
    this.generateError.set(null);
    // Feature 016: a keyword click supersedes any pending/active custom-Define state.
    this.selectionAffordance.set(null);
    this.selectionError.set(null);
    this.customDefinition.set(false);
    this.activeKeywordRect.set(null);
    this.definitionLoading.set(false);
    this.definitionError.set(null);
  }

  /** Feature 013: clear the active keyword after any dismissal. */
  closeKeyword(): void {
    this.activeKeyword.set(null);
    this.activeKeywordAnchor.set(null);
    this.activeKeywordSubtopicId.set(null);
    this.generateError.set(null);
    // Feature 016: reset the custom-Define card state.
    this.definitionLoading.set(false);
    this.definitionError.set(null);
    this.customDefinition.set(false);
    this.activeKeywordRect.set(null);
    this.defining.set(false);
  }

  // ─── Feature 016 — custom keyword "Define" flow ────────────────────────────
  //
  // SECURITY BOUNDARY (features 016/017): the selected term is read as plain text by the directive
  // (`Selection.toString()`), and the generated definition (the custom meaning) is fed into the
  // existing `keyword-definition-card`, which renders both via escaped interpolation only. The
  // created snack's content is rendered through the shared `<app-rich-text>` safe renderer. This
  // page never uses `[innerHTML]` or `DomSanitizer`, and the custom term/meaning remain untrusted
  // (FR-013/FR-014/FR-015, FR-017..FR-019).

  /**
   * FR-001/FR-010/FR-011: a held selection arrived from the directive. Normalize and locally
   * validate it; blank/oversized selections show a meaningful error and are never submitted. A
   * valid term shows the transient, selection-anchored affordance.
   */
  onTermSelected(event: { term: string; rect: DOMRect }): void {
    const term = (event.term ?? '').trim().replace(/\s+/g, ' ');
    if (term.length === 0) {
      this.selectionAffordance.set(null);
      this.selectionError.set('Select a word or phrase to define.');
      return;
    }
    if (term.split(/\s+/).length > MAX_TERM_TOKENS) {
      this.selectionAffordance.set(null);
      this.selectionError.set('Select no more than 10 words to define.');
      return;
    }
    this.selectionError.set(null);
    this.selectionAffordance.set({ term, rect: event.rect });
  }

  /** FR-002/FR-003: hide the affordance when the selection changes or clears. */
  onSelectionCleared(): void {
    this.selectionAffordance.set(null);
  }

  /**
   * FR-006/FR-007/FR-008/FR-009: activate "Define" for the held term. At most one request is in
   * flight; the existing single card opens for the term with a loading state, then shows the
   * generated definition (or a meaningful error, adding nothing).
   */
  onDefineRequested(): void {
    if (this.defining()) {
      return;
    }
    const pending = this.selectionAffordance();
    const themeId = this.themeId;
    if (!pending) {
      return;
    }
    if (!themeId) {
      this.selectionAffordance.set(null);
      this.selectionError.set('This term cannot be defined right now.');
      return;
    }

    this.defining.set(true);
    this.selectionAffordance.set(null);
    this.selectionError.set(null);
    this.generateError.set(null);

    // Feature 017 (Option A, FR-005): pin the active entry's source subtopic at Define time so the
    // created snack attaches to the entry the meaning was read from — a regular entry's `subtopicId`
    // or a meaning entry's `sourceSubtopicId` (null when neither resolves). This mirrors feature
    // 014's capture-at-open invariant and lets the existing `onGenerateRequested` work unchanged.
    const sourceEntry = this.currentEntry();
    const sourceSubtopicId =
      sourceEntry?.kind === 'regular'
        ? sourceEntry.subtopicId
        : sourceEntry?.kind === 'meaning'
          ? sourceEntry.sourceSubtopicId
          : null;
    this.activeKeywordSubtopicId.set(sourceSubtopicId);

    // Open the existing single card for the selected term with a loading state (FR-006/FR-008).
    this.activeKeyword.set({ term: pending.term, definition: '' });
    this.activeKeywordAnchor.set(null);
    this.activeKeywordRect.set(pending.rect);
    this.customDefinition.set(true);
    this.definitionLoading.set(true);
    this.definitionError.set(null);

    this.definitionService.generate(themeId, pending.term).subscribe({
      next: (res) => {
        this.activeKeyword.set({ term: res.term, definition: res.definition });
        this.definitionLoading.set(false);
        this.definitionError.set(null);
        this.defining.set(false);
      },
      error: (err) => {
        // Show a meaningful, user-safe error and add nothing empty/placeholder (FR-009).
        this.definitionLoading.set(false);
        this.definitionError.set(this.errorService.getMessage(err));
        this.defining.set(false);
      },
    });
  }

  /**
   * Feature 014: generate a meaning snack from the active regular entry's source subtopic. At most
   * one in-flight request per keyword; on success the item is appended to the backbone (deduped by
   * id) and marked generated; on failure the card shows the error and adds nothing
   * (FR-001..FR-006, SC-002/SC-003).
   */
  onGenerateRequested(keyword: SnackKeyword): void {
    const themeId = this.themeId;
    // Use the source captured when the card opened, never the (possibly navigated) current entry.
    const subtopicId = this.activeKeywordSubtopicId();
    if (!themeId || !subtopicId) {
      this.generateError.set('This keyword cannot be generated right now.');
      return;
    }
    const key = generateKey(subtopicId, keyword.term);
    if (this.generatingKeys.has(key)) {
      return;
    }
    this.generatingKeys.add(key);
    this.generateError.set(null);
    this.generateStates.update((m) => ({ ...m, [key]: 'loading' }));

    this.meaningSnackService
      .generate(themeId, subtopicId, keyword.term, keyword.definition)
      .subscribe({
        next: (res) => {
          this.generatingKeys.delete(key);
          this.meaningSnackService.markGenerated(res.meaningSnack);
          this.meaningSnacks.update((list) =>
            list.some((m) => m.id === res.meaningSnack.id) ? list : [...list, res.meaningSnack],
          );
          this.appState.meaningSnacks.set(this.meaningSnacks());
          this.generateStates.update((m) => ({ ...m, [key]: 'done' }));
          this.clampStoredIndex();
        },
        error: (err) => {
          this.generatingKeys.delete(key);
          this.generateStates.update((m) => ({ ...m, [key]: 'idle' }));
          this.generateError.set(this.errorService.getMessage(err));
        },
      });
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

/** Feature 014: project a regular batch item into the backbone union (entryId = subtopicId). */
function toRegularEntry(item: BatchItem): RegularEntry {
  return { entryId: item.subtopicId, kind: 'regular', ...item };
}

/** Feature 014: project a meaning snack into the backbone union (entryId = `meaning:<id>`). */
function toMeaningEntry(meaning: MeaningSnack): MeaningEntry {
  return {
    entryId: `meaning:${meaning.id}`,
    kind: 'meaning',
    sourceSubtopicId: meaning.sourceSubtopicId,
    meaning,
  };
}

/** Feature 014: the shared generated-key shape (`${subtopicId}::${term.toLowerCase()}`). */
function generateKey(subtopicId: string, term: string): string {
  return `${subtopicId}::${(term ?? '').toLowerCase()}`;
}
