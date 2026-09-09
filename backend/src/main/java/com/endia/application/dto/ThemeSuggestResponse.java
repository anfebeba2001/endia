package com.endia.application.dto;

import java.util.List;
import java.util.UUID;

public record ThemeSuggestResponse(
        UUID themeId,
        List<SubtopicItem> subtopics
) {
    public record SubtopicItem(UUID id, String name) {
    }
}
