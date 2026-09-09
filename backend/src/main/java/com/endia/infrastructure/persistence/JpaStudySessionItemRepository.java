package com.endia.infrastructure.persistence;

import com.endia.domain.model.StudySessionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaStudySessionItemRepository extends JpaRepository<StudySessionItem, UUID> {

    List<StudySessionItem> findBySessionId(UUID sessionId);
}
