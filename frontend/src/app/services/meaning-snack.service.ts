import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  MeaningSnack,
  MeaningSnackGenerateResponse,
  MeaningSnackListResponse,
} from '../models/meaning-snack.model';

/**
 * HTTP + shared "already generated" state for meaning snacks (feature 014, FR-001/FR-005/FR-006/
 * FR-018/FR-020). The generated-key set is keyed by `${subtopicId}::${term.toLowerCase()}` so the
 * definition card can reflect the `done` state across surfaces and page reloads.
 */
@Injectable({ providedIn: 'root' })
export class MeaningSnackService {
  private readonly baseUrl = '/api/meaning-snacks';

  /** Keys of meaning snacks already generated (per subtopic + case-insensitive term). */
  readonly generatedKeys = signal<ReadonlySet<string>>(new Set<string>());

  constructor(private http: HttpClient) {}

  generate(
    themeId: string,
    subtopicId: string,
    term: string,
    definition: string,
  ): Observable<MeaningSnackGenerateResponse> {
    return this.http.post<MeaningSnackGenerateResponse>(`${this.baseUrl}/generate`, {
      themeId,
      subtopicId,
      term,
      definition,
    });
  }

  listByTheme(themeId: string): Observable<MeaningSnackListResponse> {
    return this.http.get<MeaningSnackListResponse>(this.baseUrl, {
      params: { themeId },
    });
  }

  hasGenerated(subtopicId: string, term: string): boolean {
    return this.generatedKeys().has(key(subtopicId, term));
  }

  markGenerated(snack: MeaningSnack): void {
    this.generatedKeys.update((keys) => {
      const next = new Set(keys);
      next.add(key(snack.sourceSubtopicId, snack.sourceTerm));
      return next;
    });
  }

  /**
   * Drop the accumulated generated-key state so it does not leak across themes/sessions
   * (called from {@link AppStateService.reset}).
   */
  reset(): void {
    this.generatedKeys.set(new Set<string>());
  }
}

function key(subtopicId: string, term: string): string {
  return `${subtopicId}::${(term ?? '').toLowerCase()}`;
}
