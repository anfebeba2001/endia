package com.endia.domain.enums;

/**
 * Explicit kind marker for a snack entry (feature 014, FR-016/FR-017).
 *
 * <p>A regular {@link com.endia.domain.model.Snack} has no kind column and is quiz-bearing;
 * a {@link com.endia.domain.model.MeaningSnack} is always {@code MEANING} and is structurally
 * incapable of carrying a quiz. The discriminator is persisted (column {@code kind}) and carried
 * in the API ({@code "kind":"MEANING"}) so no surface can conflate the two.
 */
public enum SnackKind {
    MEANING
}
