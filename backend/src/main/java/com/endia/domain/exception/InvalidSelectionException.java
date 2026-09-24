package com.endia.domain.exception;

/**
 * Thrown when a user text selection cannot be defined: it is blank/whitespace-only, or it exceeds
 * the maximum allowed number of whitespace-separated tokens (feature 016, FR-010/FR-011).
 *
 * <p>Maps to HTTP 400 Bad Request with the standard {@code VALIDATION_ERROR} shape via the existing
 * {@code GlobalExceptionHandler} (no handler change), mirroring {@link InvalidOptionIndexException}.
 */
public class InvalidSelectionException extends DomainException {

    public InvalidSelectionException(String message) {
        super(400, "VALIDATION_ERROR", message);
    }
}
