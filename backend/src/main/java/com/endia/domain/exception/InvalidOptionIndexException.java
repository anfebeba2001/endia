package com.endia.domain.exception;

/**
 * Thrown when an invalid option index is submitted for a quiz question.
 * Maps to HTTP 400 Bad Request.
 */
public class InvalidOptionIndexException extends DomainException {

    public InvalidOptionIndexException(int index) {
        super(400, "VALIDATION_ERROR", "Option index must be between 0 and 3: " + index);
    }
}
