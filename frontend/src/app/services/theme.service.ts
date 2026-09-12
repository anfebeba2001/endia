import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ThemeSuggestResponse, SubtopicAddResponse } from '../models/theme.model';
import { ThemeHistory } from '../models/history.model';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly baseUrl = '/api/themes';

  constructor(private http: HttpClient) {}

  suggest(prompt: string): Observable<ThemeSuggestResponse> {
    return this.http.post<ThemeSuggestResponse>(`${this.baseUrl}/suggest`, { prompt });
  }

  /** Add user-proposed subtopics (FR-019). */
  addSubtopics(themeId: string, names: string[]): Observable<SubtopicAddResponse> {
    return this.http.post<SubtopicAddResponse>(`${this.baseUrl}/${themeId}/subtopics`, { names });
  }

  /** Read-only history of every created theme, newest first (feature 011, FR-009). */
  listHistory(): Observable<ThemeHistory[]> {
    return this.http.get<ThemeHistory[]>(this.baseUrl);
  }
}
