package com.endia.application.service;

import com.endia.domain.enums.SubtopicSource;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Adds user-proposed subtopics (FR-019) with deterministic dedup (FR-021):
 * normalized names (trim + lowercase + collapse internal whitespace) are deduped
 * against existing theme subtopics (any source) and within the incoming request.
 * Persisted subtopics get {@code source = USER_PROPOSED}.
 */
@Service
public class SubtopicService {

    private final JpaThemeRepository themeRepository;
    private final JpaSubtopicRepository subtopicRepository;

    public SubtopicService(JpaThemeRepository themeRepository, JpaSubtopicRepository subtopicRepository) {
        this.themeRepository = themeRepository;
        this.subtopicRepository = subtopicRepository;
    }

    @Transactional
    public AddResult addSubtopics(UUID themeId, List<String> names) {
        Theme theme = themeRepository.findById(themeId)
                .orElseThrow(() -> new EntityNotFoundException("Theme", themeId));

        Set<String> existing = new HashSet<>();
        for (Subtopic s : subtopicRepository.findByThemeId(themeId)) {
            existing.add(normalize(s.getName()));
        }

        List<Subtopic> added = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        Set<String> seenInRequest = new HashSet<>();

        for (String raw : names) {
            String normalized = normalize(raw);
            if (normalized == null || normalized.isEmpty()) {
                continue;
            }
            if (existing.contains(normalized) || seenInRequest.contains(normalized)) {
                duplicates.add(normalized);
                continue;
            }
            Subtopic subtopic = new Subtopic(theme, raw.trim());
            subtopic.setSource(SubtopicSource.USER_PROPOSED);
            added.add(subtopicRepository.save(subtopic));
            seenInRequest.add(normalized);
            existing.add(normalized);
        }

        return new AddResult(themeId, added, duplicates);
    }

    String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    public record AddResult(UUID themeId, List<Subtopic> added, List<String> duplicates) {
    }
}
