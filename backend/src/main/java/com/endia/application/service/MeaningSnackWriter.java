package com.endia.application.service;

import com.endia.domain.model.MeaningSnack;
import com.endia.infrastructure.persistence.JpaMeaningSnackRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Transactional persistence boundary for meaning snacks (feature 014, FR-005/FR-006).
 *
 * <p>The duplicate-race recovery needs two <strong>independent</strong> transactions:
 * <ul>
 *   <li>the insert must be flushed and committed on its own, so a
 *       {@code ux_meaning_snack_source} violation is raised while
 *       {@link MeaningSnackService#generate} is still on the stack and can be caught; and</li>
 *   <li>the winner re-query must run in a <em>fresh</em> transaction, because after the failed
 *       insert its transaction is marked rollback-only and a same-transaction read would fail or
 *       return stale data.</li>
 * </ul>
 *
 * <p>Both methods use {@link Propagation#REQUIRES_NEW} so the boundaries hold even if a caller
 * ever supplies an ambient transaction. Keeping this in a separate bean (rather than a
 * self-invoked method) guarantees the Spring proxy actually applies the propagation.
 */
@Component
public class MeaningSnackWriter {

    private final JpaMeaningSnackRepository meaningSnackRepository;

    public MeaningSnackWriter(JpaMeaningSnackRepository meaningSnackRepository) {
        this.meaningSnackRepository = meaningSnackRepository;
    }

    /**
     * Persist the new snack in its own transaction and flush immediately, so a unique-index
     * violation surfaces as a {@code DataIntegrityViolationException} from this call rather than
     * from a later commit after {@code generate()} has returned.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MeaningSnack insert(MeaningSnack snack) {
        return meaningSnackRepository.saveAndFlush(snack);
    }

    /** Re-read the race winner in a fresh transaction after a failed insert (FR-006). */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<MeaningSnack> findExisting(UUID subtopicId, String term) {
        return meaningSnackRepository.findBySourceSubtopicIdAndSourceTermIgnoreCase(subtopicId, term);
    }
}
