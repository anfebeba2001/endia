import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DefinitionGenerateResponse } from '../models/definition.model';

/**
 * HTTP client for the provider-agnostic definition-generation endpoint (feature 016, FR-004).
 * The page owns the request/loading/error state; this service is a thin transport only.
 */
@Injectable({ providedIn: 'root' })
export class DefinitionService {
  private readonly baseUrl = '/api/definitions';

  constructor(private http: HttpClient) {}

  generate(themeId: string, term: string): Observable<DefinitionGenerateResponse> {
    return this.http.post<DefinitionGenerateResponse>(`${this.baseUrl}/generate`, {
      themeId,
      term,
    });
  }
}
