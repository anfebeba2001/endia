package com.endia.infrastructure.llm;

import com.endia.domain.enums.DragDropVariant;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads and renders prompt templates from classpath resources (resources/prompts/*.md).
 */
@Component
public class LlmPromptTemplates {

    public String suggestSubtopics(String theme) {
        return render("prompts/suggest-subtopics.md", "{{theme}}", theme);
    }

    public String generateSnack(String theme, String subtopic) {
        String tpl = load("prompts/generate-snack.md");
        return tpl.replace("{{theme}}", theme).replace("{{subtopic}}", subtopic);
    }

    public String generateQuiz(String theme, String subtopic, int count, DragDropVariant variant) {
        String tpl = load("prompts/generate-quiz.md");
        return tpl.replace("{{theme}}", theme)
                .replace("{{subtopic}}", subtopic)
                .replace("{{count}}", String.valueOf(count))
                .replace("{{variant}}", variant.name());
    }

    /** Feature 014: the externalized meaning-snack prompt, parameterized by theme/term/definition. */
    public String generateMeaningSnack(String theme, String term, String definition) {
        String tpl = load("prompts/generate-meaning-snack.md");
        return tpl.replace("{{theme}}", theme)
                .replace("{{term}}", term)
                .replace("{{definition}}", definition);
    }

    /** Feature 016: the externalized short-definition prompt, parameterized by theme/term. */
    public String generateDefinition(String theme, String term) {
        String tpl = load("prompts/generate-definition.md");
        return tpl.replace("{{theme}}", theme).replace("{{term}}", term);
    }

    private String render(String path, String placeholder, String value) {
        return load(path).replace(placeholder, value);
    }

    private String load(String path) {
        try {
            var resource = new ClassPathResource(path);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt template: " + path, e);
        }
    }
}
