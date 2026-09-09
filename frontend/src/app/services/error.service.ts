import { Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse } from '../models/api-response.model';

@Injectable({ providedIn: 'root' })
export class ErrorService {
  getMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ErrorResponse | undefined;
      if (body?.cause) {
        return this.fromCause(body.cause) ?? body.message ?? this.fromStatus(error.status);
      }
      if (body?.message) {
        return body.message;
      }
      return this.fromStatus(error.status);
    }
    return 'An unexpected error occurred.';
  }

  private fromCause(cause: string): string | null {
    switch (cause) {
      case 'UNAVAILABLE':
        return 'The AI service is temporarily unavailable. Please try again shortly.';
      case 'TIMEOUT':
        return 'The AI service took too long to respond. Please try again.';
      case 'NETWORK':
        return 'Could not reach the AI service. Check your internet connection and try again.';
      case 'QUOTA':
        return 'The AI service quota or rate limit was reached. Try again shortly.';
      case 'MALFORMED':
        return 'The AI returned an unexpected response. Please try again.';
      default:
        return null;
    }
  }

  private fromStatus(status: number): string {
    if (status === 0) {
      return 'Unable to reach the server. Please check your connection.';
    }
    if (status >= 500) {
      return 'Something went wrong on the server. Please try again later.';
    }
    return `Request failed with status ${status}.`;
  }
}
