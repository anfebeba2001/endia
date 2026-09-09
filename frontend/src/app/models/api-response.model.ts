export interface ErrorResponse {
  error: string;
  message: string;
  field?: string;
  cause?: string;
}
