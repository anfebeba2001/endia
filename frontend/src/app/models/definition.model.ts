/**
 * Generated short definition for a user-selected term (feature 016, FR-004/FR-008).
 * Mirrors `POST /api/definitions/generate` exactly: two fields, nothing persisted.
 */
export interface DefinitionGenerateResponse {
  /** The normalized selected term, echoed by the backend. */
  term: string;
  /** The generated short definition (at most 20 words); untrusted plain text. */
  definition: string;
}
