package com.endia.infrastructure.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature 014 (US1): the externalized meaning-snack prompt must ask for exactly the
 * {@code concept/metaphor/whyItMatters/related} JSON shape (no {@code keywords}), forbid quiz
 * content, and preserve the feature-012 formatting/forbidden-markup rules (FR-002, FR-007, FR-008,
 * SC-001).
 *
 * <p>Assertions are written against the rendered prompt so they cover exactly what the provider
 * sends to the model.
 */
class GenerateMeaningSnackPromptTest {

    private final LlmPromptTemplates templates = new LlmPromptTemplates();

    @Test
    void promptSubstitutesThemeTermAndDefinition() {
        String prompt = templates.generateMeaningSnack("Photosynthesis", "photosynthesis", "light to sugar");

        assertTrue(prompt.contains("Photosynthesis"));
        assertTrue(prompt.contains("photosynthesis"));
        assertTrue(prompt.contains("light to sugar"));
        assertFalse(prompt.contains("{{theme}}"));
        assertFalse(prompt.contains("{{term}}"));
        assertFalse(prompt.contains("{{definition}}"));
    }

    @Test
    void promptRequestsTheExactMeaningSnackJsonShape() {
        String prompt = templates.generateMeaningSnack("Photosynthesis", "photosynthesis", "light to sugar");

        for (String field : List.of("concept", "metaphor", "whyItMatters", "related")) {
            assertTrue(prompt.contains("\"" + field + "\""),
                    "prompt must specify the field " + field);
        }
    }

    @Test
    void promptForbidsQuizContent() {
        String prompt = templates.generateMeaningSnack("Photosynthesis", "photosynthesis", "light to sugar");
        String lower = prompt.toLowerCase();

        assertTrue(lower.contains("quiz"), "prompt must mention quiz content is forbidden");
        assertTrue(lower.contains("question"), "prompt must forbid quiz questions");
        assertTrue(lower.contains("option"), "prompt must forbid multiple-choice options");
        assertTrue(lower.contains("answer"), "prompt must forbid answers");
    }

    @Test
    void promptForbidsAKeywordsArray() {
        String prompt = templates.generateMeaningSnack("Photosynthesis", "photosynthesis", "light to sugar");
        String lower = prompt.toLowerCase();

        assertTrue(lower.contains("keywords"), "prompt must explicitly address a keywords array");
        assertTrue(lower.contains("do not") || lower.contains("no "),
                "prompt must state that keywords are not to be emitted");
    }

    @Test
    void promptPreservesFeature012FormattingRules() {
        String prompt = templates.generateMeaningSnack("Photosynthesis", "photosynthesis", "light to sugar");
        String lower = prompt.toLowerCase();

        assertTrue(prompt.contains("**"), "prompt must instruct the ** bold marker");
        assertTrue(lower.contains("bold"));
        assertTrue(prompt.contains("- "), "prompt must instruct '- ' bullet lines");
        assertTrue(lower.contains("bullet"));
        assertTrue(lower.contains("paragraph"));
        assertTrue(lower.contains("blank line"), "prompt must explain blank-line paragraph breaks");
    }

    @Test
    void promptPreservesForbiddenMarkupList() {
        String prompt = templates.generateMeaningSnack("Photosynthesis", "photosynthesis", "light to sugar");
        String lower = prompt.toLowerCase();

        for (String forbidden : List.of(
                "html", "link", "image", "script", "heading", "table", "code", "style")) {
            assertTrue(lower.contains(forbidden), "prompt must forbid " + forbidden + " markup");
        }
    }
}
