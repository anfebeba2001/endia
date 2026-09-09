import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SnackGenerateResponse } from '../models/snack.model';

@Injectable({ providedIn: 'root' })
export class SnackService {
  private readonly baseUrl = '/api/snacks';

  constructor(private http: HttpClient) {}

  generate(themeId: string, subtopicIds: string[]): Observable<SnackGenerateResponse> {
    return this.http.post<SnackGenerateResponse>(`${this.baseUrl}/generate`, {
      themeId,
      subtopicIds,
    });
  }
}
