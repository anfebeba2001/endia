import { Injectable, InjectionToken, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, interval, race, throwError, timer } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs';
import { BatchCreateResponse, BatchStatusResponse } from '../models/batch.model';

/**
 * Configurable maximum polling lifetime (FR-010). Defaults to 120 s; deployments and
 * tests can override it through the DI seam in `app.config.ts`.
 */
export const MAX_POLLING_WINDOW_MS = new InjectionToken<number>('MAX_POLLING_WINDOW_MS', {
  providedIn: 'root',
  factory: () => 120_000,
});

/** Raised when a batch stays non-terminal for longer than the configured polling window (FR-010). */
export class BatchPollingTimeoutError extends Error {
  constructor(public readonly maxWindowMs: number) {
    super(
      `Generation timed out after ${Math.round(maxWindowMs / 1000)}s. ` +
        `The server took too long to respond. Please try again.`,
    );
    this.name = 'BatchPollingTimeoutError';
  }
}

@Injectable({ providedIn: 'root' })
export class BatchService {
  private readonly baseUrl = '/api/batches';
  private readonly maxWindowMs = inject(MAX_POLLING_WINDOW_MS);

  constructor(private http: HttpClient) {}

  /** One logical batch trigger: snacks + quiz prefetch (FR-001). */
  createBatch(themeId: string, subtopicIds: string[]): Observable<BatchCreateResponse> {
    return this.http.post<BatchCreateResponse>(this.baseUrl, {
      themeId,
      subtopicIds,
    });
  }

  /**
   * Poll batch status on a ~1.5s interval, stopping on any terminal status (FR-008/FR-009)
   * and bounded by a total maximum lifetime (FR-010). On timeout the observable errors with
   * a {@link BatchPollingTimeoutError} so the UI can surface a terminal, user-visible error.
   */
  pollStatus(
    batchId: string,
    intervalMs = 1500,
    maxWindowMs = this.maxWindowMs,
  ): Observable<BatchStatusResponse> {
    const polls$ = interval(intervalMs).pipe(
      switchMap(() => this.http.get<BatchStatusResponse>(`${this.baseUrl}/${batchId}`)),
      takeWhile((s) => !this.isTerminal(s), true),
    );
    const deadline$ = timer(maxWindowMs).pipe(
      switchMap(() => throwError(() => new BatchPollingTimeoutError(maxWindowMs))),
    );
    return race(polls$, deadline$);
  }

  /** Terminality is defined negatively: only `IN_PROGRESS` is non-terminal (FR-008). */
  private isTerminal(status: BatchStatusResponse): boolean {
    return status.status !== 'IN_PROGRESS';
  }
}
