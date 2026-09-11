package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.exception.LlmGenerationException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ThemeService {

    private final LlmProvider llmProvider;
    private final JpaThemeRepository themeRepository;
    private final JpaSubtopicRepository subtopicRepository;

    public ThemeService(LlmProvider llmProvider,
                        JpaThemeRepository themeRepository,
                        JpaSubtopicRepository subtopicRepository) {
        this.llmProvider = llmProvider;
        this.themeRepository = themeRepository;
        this.subtopicRepository = subtopicRepository;
    }

    @Transactional
    public ThemeSuggestResult suggestSubtopics(String prompt) {
        List<String> names;
        try {
            names = llmProvider.suggestSubtopics(prompt);
        } catch (LlmGenerationException e) {
            throw e;
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException("The AI service is temporarily unavailable. Please try again later.", e);
        }

        if (names == null || names.isEmpty()) {
            throw new ServiceUnavailableException("The AI service returned no subtopics. Try a clearer theme.");
        }

        Theme theme = new Theme(prompt);
        themeRepository.save(theme);

        List<Subtopic> subtopics = new ArrayList<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                Subtopic subtopic = new Subtopic(theme, name.trim());
                subtopics.add(subtopicRepository.save(subtopic));
            }
        }

        theme.setSubtopics(subtopics);

        return new ThemeSuggestResult(theme.getId(), subtopics);
    }

    public record ThemeSuggestResult(java.util.UUID themeId, List<Subtopic> subtopics) {
    }
}
