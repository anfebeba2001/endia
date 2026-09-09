package com.endia.domain.exception;

/**
 * Thrown when an entity is not found. Maps to HTTP 404 Not Found.
 */
public class EntityNotFoundException extends DomainException {

    public EntityNotFoundException(String entityName, java.util.UUID id) {
        super(404, "NOT_FOUND", entityName + " not found with ID: " + id);
    }

    public EntityNotFoundException(String message) {
        super(404, "NOT_FOUND", message);
    }
}
