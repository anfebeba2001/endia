import { Injectable } from '@angular/core';

/** Maximum user-visible generation cycles per target per budget (initial + 2 reattempts). */
export const MAX_SNACK_ATTEMPTS = 3;

/**
 * Per-generation-target attempt budget and in-flight guard for the snacks flow (FR-001/002/006/007).
 *
 * Deliberately dependency-free (no HttpClient, no Angular DI beyond `@Injectable`) so the
 * pure budget logic can be unit-tested directly if a frontend unit runner is added.
 * State is keyed by `subtopicId` and lives only for the browser session — it is not persisted.
 */
@Injectable({ providedIn: 'root' })
export class SnackRetryService {
  private readonly attempts = new Map<string, number>();
  private readonly inFlight = new Set<string>();

  /** Clear all state for a brand-new initial batch/cycle. */
  resetAll(): void {
    this.attempts.clear();
    this.inFlight.clear();
  }

  /** Record that a new generation cycle has started for the target; returns the new count. */
  registerAttempt(target: string): number {
    const next = (this.attempts.get(target) ?? 0) + 1;
    this.attempts.set(target, next);
    return next;
  }

  /** Number of user-visible generation cycles started for the target (1..3). */
  attemptsFor(target: string): number {
    return this.attempts.get(target) ?? 0;
  }

  /** True iff the target has no cycle in flight and has not exhausted its 3-attempt budget. */
  canRetry(target: string): boolean {
    return !this.inFlight.has(target) && this.attemptsFor(target) < MAX_SNACK_ATTEMPTS;
  }

  markInFlight(target: string): void {
    this.inFlight.add(target);
  }

  clearInFlight(target: string): void {
    this.inFlight.delete(target);
  }

  isInFlight(target: string): boolean {
    return this.inFlight.has(target);
  }

  hasInFlight(): boolean {
    return this.inFlight.size > 0;
  }

  /** Reset a target's automatic budget to a fresh cycle (used by manual retry, FR-006). */
  resetBudget(target: string): void {
    this.attempts.set(target, 0);
  }
}
