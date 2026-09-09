import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ThemeSuggestResponse } from '../models/theme.model';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly baseUrl = '/api/themes';

  constructor(private http: HttpClient) {}

  suggest(prompt: string): Observable<ThemeSuggestResponse> {
    return this.http.post<ThemeSuggestResponse>(`${this.baseUrl}/suggest`, { prompt });
  }
}
