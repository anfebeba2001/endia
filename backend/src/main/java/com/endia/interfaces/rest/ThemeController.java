package com.endia.interfaces.rest;

import com.endia.application.service.ThemeService;
import com.endia.interfaces.rest.dto.ThemeSuggestApiRequest;
import com.endia.interfaces.rest.dto.ThemeSuggestApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
}
