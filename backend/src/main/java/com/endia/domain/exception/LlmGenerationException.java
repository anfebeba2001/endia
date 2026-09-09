package com.endia.domain.exception;

import com.endia.domain.enums.LlmErrorCause;

/**
 * Typed exception carrying an {@link LlmErrorCause} and a retryability flag.
 * Thrown by the LLM provider layer; mapped by the global exception handler to an
 * {@code ErrorResponse} with a matching status and a machine-readable {@code cause}.
 */
public class LlmGenerationException extends DomainException {

    private final LlmErrorCause cause;
    private final boolean retryable;

    public LlmGenerationException(LlmErrorCause cause, boolean retryable, String message) {
        super(statusFor(cause), cause.name(), message);
        this.cause = cause;
        this.retryable = retryable;
    }

    public LlmGenerationException(LlmErrorCause cause, boolean retryable, String message, Throwable underlying) {
        super(statusFor(cause), cause.name(), message);
        this.cause = cause;
        this.retryable = retryable;
        initCause(underlying);
    }

    public LlmErrorCause getCauseType() {
        return cause;
    }

    public boolean isRetryable() {
        return retryable;
    }

    private static int statusFor(LlmErrorCause cause) {
        return switch (cause) {
            case QUOTA -> 429;
            case MALFORMED -> 400;
            default -> 503;
        };
    }
}
