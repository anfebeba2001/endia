package com.endia.infrastructure.persistence;

import com.endia.domain.model.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaStudySessionRepository extends JpaRepository<StudySession, UUID> {
}
