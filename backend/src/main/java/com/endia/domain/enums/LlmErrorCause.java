package com.endia.domain.enums;

/**
 * Canonical vocabulary of LLM failure causes (FR-015/SC-004).
 * Exposed via {@code ErrorResponse.cause} so the frontend can surface a specific message.
 */
public enum LlmErrorCause {
    UNAVAILABLE,
    TIMEOUT,
    NETWORK,
    QUOTA,
    MALFORMED
}
