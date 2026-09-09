package com.endia.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ThemeSuggestRequest(
        @NotBlank(message = "Theme is required")
        @Size(min = 2, max = 500, message = "Theme must be between 2 and 500 characters")
        String prompt
) {
}
