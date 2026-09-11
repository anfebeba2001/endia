package com.endia.application.port;

import com.endia.domain.enums.SnackGenerationPhase;

/**
 * Application-layer callback port invoked by {@code SnackService} at the exact code point where
 * each real generation step begins (feature 008, FR-002). Keeps the generator free of any
 * registry/singleton dependency; single-shot generation uses {@link #NOOP}.
 */
@FunctionalInterface
public interface SnackPhaseListener {

    SnackPhaseListener NOOP = phase -> {
    };

    void onPhase(SnackGenerationPhase phase);
}
