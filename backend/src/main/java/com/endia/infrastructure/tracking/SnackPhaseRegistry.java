package com.endia.infrastructure.tracking;

import com.endia.domain.enums.SnackGenerationPhase;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of the current snack generation phase per batch item, keyed by subtopic id
 * (feature 008, FR-008). The {@code @Async} worker writes and the HTTP request thread reads, so a
 * {@link ConcurrentHashMap} provides the required happens-before for the enum reference. Never
 * persisted; entries are cleared before and after each generation so a phase cannot outlive an item.
 */
@Component
public class SnackPhaseRegistry {

    private final ConcurrentHashMap<UUID, SnackGenerationPhase> phases = new ConcurrentHashMap<>();

    public void update(UUID subtopicId, SnackGenerationPhase phase) {
        if (subtopicId == null || phase == null) {
            return;
        }
        phases.put(subtopicId, phase);
    }

    public SnackGenerationPhase current(UUID subtopicId) {
        if (subtopicId == null) {
            return null;
        }
        return phases.get(subtopicId);
    }

    public void clear(UUID subtopicId) {
        if (subtopicId == null) {
            return;
        }
        phases.remove(subtopicId);
    }
}
