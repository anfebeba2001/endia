package com.endia.infrastructure.persistence;

import com.endia.domain.model.MeaningSnack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link MeaningSnack} (feature 014). The idempotency query is the primary
 * duplicate guard; the {@code ux_meaning_snack_source} unique index is defence-in-depth
 * (data-model.md §4.4).
 */
public interface JpaMeaningSnackRepository extends JpaRepository<MeaningSnack, UUID> {

    /** Theme-scoped listing, ordered for stable rendering (FR-018/FR-020, contract §2.2). */
    List<MeaningSnack> findByThemeIdOrderByCreatedAtAsc(UUID themeId);

    /** Case-insensitive idempotency lookup for {@code (sourceSubtopicId, sourceTerm)} (FR-006). */
    Optional<MeaningSnack> findBySourceSubtopicIdAndSourceTermIgnoreCase(UUID subtopicId, String term);
}
