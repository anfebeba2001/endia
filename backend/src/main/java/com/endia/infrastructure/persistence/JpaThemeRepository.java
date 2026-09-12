package com.endia.infrastructure.persistence;

import com.endia.domain.model.Theme;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaThemeRepository extends JpaRepository<Theme, UUID> {

    /**
     * History read (feature 011): fetch all themes newest-first with their subtopics initialized.
     * Only the single {@code subtopics} bag is fetched (safe); snacks and quiz questions are
     * batch-loaded separately to avoid {@code MultipleBagFetchException}.
     */
    @EntityGraph(attributePaths = {"subtopics"})
    List<Theme> findAllByOrderByCreatedAtDesc();
}
