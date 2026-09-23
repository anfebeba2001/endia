package com.endia.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

/**
 * Theme-scoped listing response (feature 014, FR-018/FR-020). {@code meaningSnacks} is always
 * present and is an empty list when the theme has none.
 */
public record MeaningSnackListApiResponse(
        UUID themeId,
        List<MeaningSnackItem> meaningSnacks
) {
}
