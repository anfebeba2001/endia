package com.endia.domain.exception;

/**
 * Thrown when an external service (e.g. LLM) is temporarily unavailable.
 * Maps to HTTP 503 Service Unavailable.
 */
public class ServiceUnavailableException extends DomainException {

    public ServiceUnavailableException(String message) {
        super(503, "SERVICE_UNAVAILABLE", message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(503, "SERVICE_UNAVAILABLE", message);
        initCause(cause);
    }
}
