package com.endia.infrastructure.persistence;

import com.endia.domain.model.Subtopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaSubtopicRepository extends JpaRepository<Subtopic, UUID> {

    List<Subtopic> findByThemeId(UUID themeId);
}
