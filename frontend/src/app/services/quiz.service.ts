import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuizGenerateResponse, QuizAnswerResponse, QuizSummaryResponse } from '../models/quiz.model';

@Injectable({ providedIn: 'root' })
export class QuizService {
  private readonly baseUrl = '/api/quiz';

  constructor(private http: HttpClient) {}

  generate(themeId: string, subtopicId: string, count = 4): Observable<QuizGenerateResponse> {
    return this.http.post<QuizGenerateResponse>(`${this.baseUrl}/generate`, {
      themeId,
      subtopicId,
      count,
    });
  }

  answer(questionId: string, chosenIndex: number): Observable<QuizAnswerResponse> {
    return this.http.post<QuizAnswerResponse>(`${this.baseUrl}/answer`, {
      questionId,
      chosenIndex,
    });
  }

  summary(subtopicId: string): Observable<QuizSummaryResponse> {
    return this.http.get<QuizSummaryResponse>(`${this.baseUrl}/summary/${subtopicId}`);
  }
}
