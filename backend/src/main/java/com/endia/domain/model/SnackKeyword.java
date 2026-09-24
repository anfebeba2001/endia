package com.endia.domain.model;

/**
 * A key term of a snack together with a short (at most 20 words) definition
 * (feature 013, FR-011; feature 015).
 *
 * <p>This is a pure domain value object: no JPA annotations and no framework imports.
 * It is Jackson-serializable because it is persisted inside the snack's {@code keywords}
 * JSON column through {@link com.endia.infrastructure.persistence.JpaSnackKeywordListConverter}.
 *
 * <p>The compact constructor normalizes {@code null} to {@code ""} but performs
 * <strong>no</strong> truncation, dropping or de-duplication — that policy lives at the
 * service boundary ({@code SnackService.normalizeKeywords}) so the entity stays a faithful
 * representation of what was stored.
 */
public record SnackKeyword(String term, String definition) {

    public SnackKeyword {
        term = term == null ? "" : term;
        definition = definition == null ? "" : definition;
    }
}
