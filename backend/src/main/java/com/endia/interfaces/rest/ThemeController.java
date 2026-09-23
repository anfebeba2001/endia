package com.endia.interfaces.rest;

import com.endia.application.service.ThemeService;
import com.endia.interfaces.rest.dto.ThemeHistoryApiResponse;
import com.endia.interfaces.rest.dto.SnackKeywordDto;
import com.endia.interfaces.rest.dto.ThemeSuggestApiRequest;
import com.endia.interfaces.rest.dto.ThemeSuggestApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/themes")
public class ThemeController {

    private final ThemeService themeService;

    public ThemeController(ThemeService themeService) {
        this.themeService = themeService;
    }

    @PostMapping("/suggest")
    public ResponseEntity<ThemeSuggestApiResponse> suggest(@Valid @RequestBody ThemeSuggestApiRequest request) {
        ThemeService.ThemeSuggestResult result = themeService.suggestSubtopics(request.prompt());

        List<ThemeSuggestApiResponse.SubtopicItem> items = result.subtopics().stream()
                .map(s -> new ThemeSuggestApiResponse.SubtopicItem(s.getId(), s.getName(), s.getSource().name()))
                .toList();

        return ResponseEntity.ok(new ThemeSuggestApiResponse(result.themeId(), items));
    }

    /**
     * Read-only history of every created theme, newest first (feature 011, FR-001..FR-007).
     * Returns HTTP 200 with a JSON array; an empty history returns {@code []}.
     */
    @GetMapping
    public ResponseEntity<List<ThemeHistoryApiResponse>> getHistory() {
        List<ThemeHistoryApiResponse> body = themeService.listHistory().stream()
                .map(theme -> new ThemeHistoryApiResponse(
                        theme.themeId(),
                        theme.prompt(),
                        theme.createdAt(),
                        theme.subtopics().stream()
                                .map(subtopic -> new ThemeHistoryApiResponse.SubtopicItem(
                                        subtopic.id(),
                                        subtopic.name(),
                                        subtopic.snacks().stream()
                                                .map(snack -> new ThemeHistoryApiResponse.SnackItem(
                                                        snack.id(),
                                                        snack.concept(),
                                                        snack.metaphor(),
                                                        snack.whyItMatters(),
                                                        snack.related(),
                                                        snack.keywords() == null
                                                                ? java.util.List.of()
                                                                : snack.keywords().stream()
                                                                        .map(SnackKeywordDto::from)
                                                                        .toList()))
                                                .toList(),
                                        subtopic.quizQuestions().stream()
                                                .map(question -> new ThemeHistoryApiResponse.QuestionItem(
                                                        question.id(),
                                                        question.type().name(),
                                                        question.prompt(),
                                                        question.explanation()))
                                                .toList()))
                                .toList()))
                .toList();

        return ResponseEntity.ok(body);
    }
}
