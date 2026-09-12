package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.enums.QuestionType;
import com.endia.domain.exception.LlmGenerationException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.QuizQuestion;
import com.endia.domain.model.Snack;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaQuizQuestionRepository;
import com.endia.infrastructure.persistence.JpaSnackRepository;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ThemeService {

    private final LlmProvider llmProvider;
    private final JpaThemeRepository themeRepository;
    private final JpaSubtopicRepository subtopicRepository;
    private final JpaSnackRepository snackRepository;
    private final JpaQuizQuestionRepository quizQuestionRepository;

    public ThemeService(LlmProvider llmProvider,
                        JpaThemeRepository themeRepository,
                        JpaSubtopicRepository subtopicRepository,
                        JpaSnackRepository snackRepository,
                        JpaQuizQuestionRepository quizQuestionRepository) {
        this.llmProvider = llmProvider;
        this.themeRepository = themeRepository;
        this.subtopicRepository = subtopicRepository;
        this.snackRepository = snackRepository;
        this.quizQuestionRepository = quizQuestionRepository;
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

    /**
     * Read-only history (feature 011, FR-005/FR-008/FR-022).
     *
     * <p>Uses 3 fixed queries to avoid N+1 and {@code MultipleBagFetchException}: themes+subtopics
     * via one {@code @EntityGraph} fetch, then snacks and quiz questions batch-loaded by subtopic
     * id. The nested read model is assembled <em>inside</em> this read-only transaction, so no lazy
     * proxy escapes to the controller.
     */
    @Transactional(readOnly = true)
    public List<ThemeHistory> listHistory() {
        List<Theme> themes = themeRepository.findAllByOrderByCreatedAtDesc();
        if (themes.isEmpty()) {
            return List.of();
        }

        List<UUID> subtopicIds = themes.stream()
                .flatMap(theme -> theme.getSubtopics().stream())
                .map(Subtopic::getId)
                .toList();

        Map<UUID, List<Snack>> snacksBySubtopic;
        Map<UUID, List<QuizQuestion>> questionsBySubtopic;
        if (subtopicIds.isEmpty()) {
            snacksBySubtopic = Map.of();
            questionsBySubtopic = Map.of();
        } else {
            snacksBySubtopic = snackRepository.findBySubtopicIdIn(subtopicIds).stream()
                    .collect(Collectors.groupingBy(snack -> snack.getSubtopic().getId()));
            questionsBySubtopic = quizQuestionRepository.findBySubtopicIdIn(subtopicIds).stream()
                    .collect(Collectors.groupingBy(question -> question.getSubtopic().getId()));
        }

        return themes.stream()
                .map(theme -> new ThemeHistory(
                        theme.getId(),
                        theme.getPrompt(),
                        theme.getCreatedAt(),
                        theme.getSubtopics().stream()
                                .map(subtopic -> new SubtopicHistory(
                                        subtopic.getId(),
                                        subtopic.getName(),
                                        snacksBySubtopic.getOrDefault(subtopic.getId(), List.of()).stream()
                                                .map(snack -> new SnackHistory(
                                                        snack.getId(),
                                                        snack.getConcept(),
                                                        snack.getMetaphor(),
                                                        snack.getWhyItMatters(),
                                                        snack.getRelated()))
                                                .toList(),
                                        questionsBySubtopic.getOrDefault(subtopic.getId(), List.of()).stream()
                                                .map(question -> new QuestionHistory(
                                                        question.getId(),
                                                        question.getType(),
                                                        question.getPrompt(),
                                                        question.getExplanation()))
                                                .toList()))
                                .toList()))
                .toList();
    }

    public record ThemeSuggestResult(java.util.UUID themeId, List<Subtopic> subtopics) {
    }

    public record ThemeHistory(UUID themeId, String prompt, LocalDateTime createdAt,
                               List<SubtopicHistory> subtopics) {
    }

    public record SubtopicHistory(UUID id, String name, List<SnackHistory> snacks,
                                  List<QuestionHistory> quizQuestions) {
    }

    public record SnackHistory(UUID id, String concept, String metaphor, String whyItMatters,
                               List<String> related) {
    }

    public record QuestionHistory(UUID id, QuestionType type, String prompt, String explanation) {
    }
}
