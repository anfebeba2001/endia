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

  /** Answer a question: MCQ via chosenIndex, drag-drop via arrangement (FR-008). */
  answer(questionId: string, chosenIndex?: number, arrangement?: string[]): Observable<QuizAnswerResponse> {
    return this.http.post<QuizAnswerResponse>(`${this.baseUrl}/answer`, {
      questionId,
      chosenIndex: chosenIndex ?? null,
      arrangement: arrangement ?? null,
    });
  }

  summary(subtopicId: string): Observable<QuizSummaryResponse> {
    return this.http.get<QuizSummaryResponse>(`${this.baseUrl}/summary/${subtopicId}`);
  }
}
