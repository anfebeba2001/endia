package com.endia.domain.exception;

/**
 * Base class for all domain-level exceptions.
 * Carries an HTTP status code and an error code used by the exception handler.
 */
public class DomainException extends RuntimeException {

    private final int status;
    private final String errorCode;

    public DomainException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
