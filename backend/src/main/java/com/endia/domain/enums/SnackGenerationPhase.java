package com.endia.domain.enums;

/**
 * The real snack generation step an in-progress batch item is currently in (feature 008).
 * A pure domain type with zero external dependencies. Never persisted.
 */
public enum SnackGenerationPhase {
    RESOLVING_CONTEXT,
    AWAITING_MODEL,
    ASSEMBLING,
    PERSISTING
}
