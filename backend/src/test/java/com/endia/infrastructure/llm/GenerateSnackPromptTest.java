package com.endia.infrastructure.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature 012 (US1): the externalized snack prompt must instruct the model to emit the whitelisted
 * rich formatting (bold markers, bullet lines, short blank-line paragraphs), constrain formatting
 * to that whitelist, forbid arbitrary HTML/other markup, and keep the exact JSON shape
 * {@code concept/metaphor/whyItMatters/related} (FR-001..FR-004).
 *
 * <p>These assertions are intentionally written against the rendered prompt (theme/subtopic
 * substituted) so they cover exactly what the provider sends to the model.
 */
class GenerateSnackPromptTest {

    private final LlmPromptTemplates templates = new LlmPromptTemplates();

    @Test
    void promptInstructsBoldMarkerForEmphasis() {
        String prompt = templates.generateSnack("Photosynthesis", "Light reactions");

        assertTrue(prompt.contains("**"), "prompt must instruct the ** bold marker");
        assertTrue(prompt.toLowerCase().contains("bold"),
                "prompt must describe bold emphasis");
    }

    @Test
    void promptInstructsBulletLinesAndShortParagraphs() {
        String prompt = templates.generateSnack("Photosynthesis", "Light reactions");
        String lower = prompt.toLowerCase();

        assertTrue(prompt.contains("- "), "prompt must instruct '- ' bullet lines");
        assertTrue(lower.contains("bullet"), "prompt must mention bullets");
        assertTrue(lower.contains("paragraph"), "prompt must mention paragraphs");
        assertTrue(lower.contains("blank line"), "prompt must explain blank-line paragraph breaks");
    }

    @Test
    void promptConstrainsToWhitelistAndForbidsArbitraryMarkup() {
        String prompt = templates.generateSnack("Photosynthesis", "Light reactions");
        String lower = prompt.toLowerCase();

        // Whitelist is described.
        assertTrue(lower.contains("bold"));
        assertTrue(lower.contains("bullet"));
        assertTrue(lower.contains("paragraph"));

        // Every forbidden family is named so the model is explicitly constrained.
        for (String forbidden : List.of(
                "html", "link", "image", "script", "heading", "table", "code", "style")) {
            assertTrue(lower.contains(forbidden),
                    "prompt must forbid " + forbidden + " markup");
        }

        // The old blanket "No markdown" prohibition must be replaced by the whitelist.
        assertFalse(prompt.contains("No markdown"),
                "prompt must not blanket-forbid markdown now that a whitelist is allowed");
    }

    @Test
    void promptStillRequiresTheExactJsonShape() {
        String prompt = templates.generateSnack("Photosynthesis", "Light reactions");

        for (String field : List.of("concept", "metaphor", "whyItMatters", "related")) {
            assertTrue(prompt.contains("\"" + field + "\""),
                    "prompt must still specify the field " + field);
        }
        assertTrue(prompt.contains("2-3"),
                "prompt must still require 2-3 related concept names");
    }

    // ---- Feature 013 (US1): the prompt must ask for a keywords array (FR-001..FR-005) ----

    @Test
    void promptRequestsKeywordsArrayWithTermAndDefinition() {
        String prompt = templates.generateSnack("Photosynthesis", "Light reactions");
        String lower = prompt.toLowerCase();

        assertTrue(lower.contains("keywords"), "prompt must ask for a keywords array");
        assertTrue(lower.contains("term"), "prompt must mention the keyword 'term' field");
        assertTrue(lower.contains("definition"), "prompt must mention the keyword 'definition' field");
        assertTrue(lower.contains("\"keywords\""),
                "prompt must specify the exact \"keywords\" JSON key");
    }

    @Test
    void promptLimitsDefinitionsToThreeWordsAndGuidesTwoToFive() {
        String prompt = templates.generateSnack("Photosynthesis", "Light reactions");
        String lower = prompt.toLowerCase();

        assertTrue(lower.contains("3 words") || lower.contains("three words"),
                "prompt must state the at-most-3-words definition limit");
        assertTrue(lower.contains("2-5") || lower.contains("2–5"),
                "prompt must state the 2-5 keyword guideline");
    }

    @Test
    void promptRequiresTermsVerbatimInBodyAndPlainTextOnly() {
        String prompt = templates.generateSnack("Photosynthesis", "Light reactions");
        String lower = prompt.toLowerCase();

        assertTrue(lower.contains("verbatim") || (lower.contains("appear") && lower.contains("body")),
                "prompt must require each term to appear verbatim in the snack body text");
        assertTrue(lower.contains("plain text"),
                "prompt must require keyword text to be plain text only");
        assertTrue(lower.contains("markup"),
                "prompt must forbid markup in keyword text");
    }

    @Test
    void promptSubstitutesThemeAndSubtopic() {
        String prompt = templates.generateSnack("Photosynthesis", "Light reactions");

        assertTrue(prompt.contains("Photosynthesis"));
        assertTrue(prompt.contains("Light reactions"));
        assertFalse(prompt.contains("{{theme}}"));
        assertFalse(prompt.contains("{{subtopic}}"));
    }
}
