import { Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse } from '../models/api-response.model';

@Injectable({ providedIn: 'root' })
export class ErrorService {
  getMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ErrorResponse | undefined;
      if (body?.message) {
        return body.message;
      }
      if (error.status === 0) {
        return 'Unable to reach the server. Please check your connection.';
      }
      if (error.status >= 500) {
        return 'Something went wrong on the server. Please try again later.';
      }
      return `Request failed with status ${error.status}.`;
    }
    return 'An unexpected error occurred.';
  }
}
