import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, interval, of } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs';
import { BatchCreateResponse, BatchStatusResponse } from '../models/batch.model';

@Injectable({ providedIn: 'root' })
export class BatchService {
  private readonly baseUrl = '/api/batches';

  constructor(private http: HttpClient) {}

  /** One logical batch trigger: snacks + quiz prefetch (FR-001). */
  createBatch(themeId: string, subtopicIds: string[]): Observable<BatchCreateResponse> {
    return this.http.post<BatchCreateResponse>(this.baseUrl, {
      themeId,
      subtopicIds,
    });
  }

  /**
   * Poll batch status on a ~1.5s interval, stopping on terminal states (FR-002/003).
   */
  pollStatus(batchId: string, intervalMs = 1500): Observable<BatchStatusResponse> {
    return interval(intervalMs).pipe(
      switchMap(() => this.http.get<BatchStatusResponse>(`${this.baseUrl}/${batchId}`)),
      takeWhile((s) => !this.isTerminal(s), true)
    );
  }

  private isTerminal(status: BatchStatusResponse): boolean {
    return status.status === 'COMPLETE' || status.status === 'FAILED';
  }
}
