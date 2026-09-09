package com.endia.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

public record ThemeSuggestApiResponse(
        UUID themeId,
        List<SubtopicItem> subtopics
) {
    public record SubtopicItem(UUID id, String name) {
    }
}
