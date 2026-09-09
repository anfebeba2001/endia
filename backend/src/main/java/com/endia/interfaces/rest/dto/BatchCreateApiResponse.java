package com.endia.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

public record BatchCreateApiResponse(
        UUID batchId,
        UUID themeId,
        String status,
        List<BatchItem> items
) {
    public record BatchItem(UUID subtopicId, String snackStatus, String quizStatus) {
    }
}
