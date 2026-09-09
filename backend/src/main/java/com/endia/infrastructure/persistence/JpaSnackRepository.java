package com.endia.infrastructure.persistence;

import com.endia.domain.model.Snack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaSnackRepository extends JpaRepository<Snack, UUID> {

    Optional<Snack> findBySubtopicId(UUID subtopicId);
}
