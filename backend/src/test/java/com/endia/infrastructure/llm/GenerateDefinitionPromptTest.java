package com.endia.infrastructure.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature 016 (US1): the externalized definition prompt must ask for exactly the
 * {@code { "definition": "..." }} JSON shape (no other fields), state the ≤20-word bound, require
 * plain text (no markup), and forbid quiz content and a {@code keywords} array (FR-004, FR-005,
 * SC-001).
 *
 * <p>Assertions are written against the rendered prompt so they cover exactly what the provider
 * sends to the model.
 */
class GenerateDefinitionPromptTest {

    private final LlmPromptTemplates templates = new LlmPromptTemplates();

    @Test
    void promptSubstitutesThemeAndTerm() {
        String prompt = templates.generateDefinition("Photosynthesis", "photosynthesis");

        assertTrue(prompt.contains("Photosynthesis"));
        assertTrue(prompt.contains("photosynthesis"));
        assertFalse(prompt.contains("{{theme}}"));
        assertFalse(prompt.contains("{{term}}"));
    }

    @Test
    void promptRequestsTheExactDefinitionJsonShape() {
        String prompt = templates.generateDefinition("Photosynthesis", "photosynthesis");

        assertTrue(prompt.contains("\"definition\""), "prompt must specify the definition field");
        // The definition field is the only requested field: no concept/metaphor/whyItMatters/related.
        for (String other : new String[] {"concept", "metaphor", "whyItMatters", "related"}) {
            assertFalse(prompt.contains("\"" + other + "\""),
                    "prompt must not request the " + other + " field");
        }
    }

    @Test
    void promptStatesTheTwentyWordBound() {
        String prompt = templates.generateDefinition("Photosynthesis", "photosynthesis");
        String lower = prompt.toLowerCase();

        assertTrue(lower.contains("20 words") || lower.contains("at most 20"),
                "prompt must state the 20-word bound");
    }

    @Test
    void promptRequiresPlainTextWithoutMarkup() {
        String prompt = templates.generateDefinition("Photosynthesis", "photosynthesis");
        String lower = prompt.toLowerCase();

        assertTrue(lower.contains("plain text"), "prompt must require plain text");
        for (String forbidden : new String[] {"bold", "bullet", "heading", "link", "html", "script", "style"}) {
            assertTrue(lower.contains(forbidden), "prompt must forbid " + forbidden + " markup");
        }
    }

    @Test
    void promptForbidsQuizContent() {
        String prompt = templates.generateDefinition("Photosynthesis", "photosynthesis");
        String lower = prompt.toLowerCase();

        assertTrue(lower.contains("quiz"), "prompt must mention quiz content is forbidden");
        assertTrue(lower.contains("question"), "prompt must forbid quiz questions");
        assertTrue(lower.contains("option"), "prompt must forbid multiple-choice options");
        assertTrue(lower.contains("answer"), "prompt must forbid answers");
    }

    @Test
    void promptForbidsAKeywordsArray() {
        String prompt = templates.generateDefinition("Photosynthesis", "photosynthesis");
        String lower = prompt.toLowerCase();

        assertTrue(lower.contains("keywords"), "prompt must explicitly address a keywords array");
        assertTrue(lower.contains("do not") || lower.contains("no "),
                "prompt must state that keywords are not to be emitted");
    }
}
