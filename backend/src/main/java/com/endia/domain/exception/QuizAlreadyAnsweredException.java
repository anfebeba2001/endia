package com.endia.domain.exception;

/**
 * Thrown when a quiz question has already been answered.
 * Maps to HTTP 409 Conflict.
 */
public class QuizAlreadyAnsweredException extends DomainException {

    public QuizAlreadyAnsweredException(java.util.UUID questionId) {
        super(409, "CONFLICT", "This question has already been answered: " + questionId);
    }
}
