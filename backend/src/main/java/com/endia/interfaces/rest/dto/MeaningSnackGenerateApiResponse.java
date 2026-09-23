package com.endia.interfaces.rest.dto;

/**
 * Idempotent generate response (feature 014, FR-005/FR-006): {@code alreadyExisted} is true when an
 * existing meaning snack was returned (no LLM call, no new row).
 */
public record MeaningSnackGenerateApiResponse(
        boolean alreadyExisted,
        MeaningSnackItem meaningSnack
) {
}
