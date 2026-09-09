package com.endia.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

public record SubtopicAddApiResponse(
        UUID themeId,
        List<AddedSubtopic> added,
        List<String> duplicates
) {
    public record AddedSubtopic(UUID id, String name, String source) {
    }
}
