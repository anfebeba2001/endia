package com.endia.infrastructure.llm;

import com.endia.application.port.LlmProvider;
import com.endia.domain.enums.DragDropVariant;
import com.endia.domain.enums.LlmErrorCause;
import com.endia.domain.exception.LlmGenerationException;
import com.endia.domain.model.DragDropContent;
import com.endia.domain.model.SnackKeyword;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * OpenAI-compatible LLM provider implemented with Java 21 RestClient.
 * Zero references to any provider classes leak into services/controllers.
 * <p>
 * Maps concrete failures to a typed {@link LlmErrorCause}: 429->QUOTA, read/connect
 * timeout->TIMEOUT, connect/DNS/reset->NETWORK, 5xx/503->UNAVAILABLE, parse/missing
 * field->MALFORMED. Each is wrapped in an {@link LlmGenerationException}.
 */
@Component
public class OpenAiLlmProvider implements LlmProvider {

    private final RestClient restClient;
    private final LlmConfig config;
    private final LlmPromptTemplates templates;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiLlmProvider(RestClient llmRestClient, LlmConfig config, LlmPromptTemplates templates) {
        this.restClient = llmRestClient;
        this.config = config;
        this.templates = templates;
    }

    @Override
    public List<String> suggestSubtopics(String theme) {
        String response = callLlm(templates.suggestSubtopics(theme));
        try {
            JsonNode root = objectMapper.readTree(response);
            List<String> result = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    result.add(node.asText());
                }
            }
            return result;
        } catch (Exception e) {
            throw malformed("Failed to parse LLM subtopic response", e);
        }
    }

    @Override
    public GeneratedSnack generateSnack(String theme, String subtopicName) {
        String response = callLlm(templates.generateSnack(theme, subtopicName));
        try {
            JsonNode node = objectMapper.readTree(response);
            String concept = node.path("concept").asText(null);
            String metaphor = node.path("metaphor").asText(null);
            String whyItMatters = node.path("whyItMatters").asText(null);
            List<String> related = new ArrayList<>();
            if (node.path("related").isArray()) {
                node.path("related").forEach(n -> related.add(n.asText()));
            }
            // Feature 013: parse keywords defensively — a missing array yields an empty list and a
            // malformed element is skipped rather than failing the whole snack (FR-006).
            List<SnackKeyword> keywords = new ArrayList<>();
            JsonNode keywordsNode = node.path("keywords");
            if (keywordsNode.isArray()) {
                keywordsNode.forEach(k -> {
                    if (k.isObject()) {
                        keywords.add(new SnackKeyword(
                                k.path("term").asText(null),
                                k.path("definition").asText(null)));
                    }
                });
            }
            return new GeneratedSnack(concept, metaphor, whyItMatters, related, keywords);
        } catch (Exception e) {
            throw malformed("Failed to parse LLM snack response", e);
        }
    }

    @Override
    public GeneratedMeaningSnack generateMeaningSnack(String theme, String term, String definition) {
        String response = callLlm(templates.generateMeaningSnack(theme, term, definition));
        try {
            JsonNode node = objectMapper.readTree(response);
            String concept = node.path("concept").asText(null);
            String metaphor = node.path("metaphor").asText(null);
            String whyItMatters = node.path("whyItMatters").asText(null);
            List<String> related = new ArrayList<>();
            if (node.path("related").isArray()) {
                node.path("related").forEach(n -> related.add(n.asText()));
            }
            // Any "keywords" the model emits is deliberately ignored: a meaning snack has none.
            return new GeneratedMeaningSnack(concept, metaphor, whyItMatters, related);
        } catch (Exception e) {
            throw malformed("Failed to parse LLM meaning-snack response", e);
        }
    }

    @Override
    public GeneratedDefinition generateDefinition(String theme, String term) {
        String response = callLlm(templates.generateDefinition(theme, term));
        try {
            JsonNode node = objectMapper.readTree(response);
            // Defensive parse: a missing field yields null; extra fields are ignored.
            return new GeneratedDefinition(node.path("definition").asText(null));
        } catch (Exception e) {
            throw malformed("Failed to parse LLM definition response", e);
        }
    }

    @Override
    public List<GeneratedQuestion> generateQuiz(String theme, String subtopicName, int count, DragDropVariant variant) {        String response = callLlm(templates.generateQuiz(theme, subtopicName, count, variant));
        try {
            JsonNode root = objectMapper.readTree(response);
            List<GeneratedQuestion> result = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode q : root) {
                    result.add(parseQuestion(q));
                }
            }
            return result;
        } catch (LlmGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw malformed("Failed to parse LLM quiz response", e);
        }
    }

    private GeneratedQuestion parseQuestion(JsonNode q) {
        String prompt = q.path("prompt").asText(null);
        String explanation = q.path("explanation").asText(null);
        String typeVal = q.path("type").asText(null);

        GeneratedQuestion.QuestionType type;
        if (typeVal == null) {
            // Backward-compat: a bare MCQ-shaped object without "type" is treated as MCQ.
            type = GeneratedQuestion.QuestionType.MULTIPLE_CHOICE;
        } else {
            try {
                type = GeneratedQuestion.QuestionType.valueOf(typeVal);
            } catch (IllegalArgumentException e) {
                throw malformed("Unknown question type: " + typeVal, e);
            }
        }

        if (type == GeneratedQuestion.QuestionType.MULTIPLE_CHOICE) {
            int correctIndex = q.path("correctIndex").asInt(-1);
            List<String> options = new ArrayList<>();
            if (q.path("options").isArray()) {
                q.path("options").forEach(o -> options.add(o.asText()));
            }
            return new GeneratedQuestion(prompt, type, options, correctIndex, null, null, explanation);
        }

        // DRAG_DROP
        JsonNode dd = q.path("dragDrop");
        DragDropVariant dragVariant = null;
        String variantVal = q.path("variant").asText(null);
        if (variantVal != null) {
            try {
                dragVariant = DragDropVariant.valueOf(variantVal);
            } catch (IllegalArgumentException e) {
                throw malformed("Unknown drag-drop variant: " + variantVal, e);
            }
        }
        if (dd.isMissingNode() || dd.isNull()) {
            throw malformed("Drag-drop question missing 'dragDrop' payload", null);
        }
        List<String> items = stringList(dd, "items");
        List<String> categories = stringList(dd, "categories");
        List<String> correctOrder = stringList(dd, "correctOrder");
        Map<String, String> pairs = stringMap(dd, "pairs");
        DragDropContent content = new DragDropContent(dragVariant, items, categories, pairs, correctOrder);
        return new GeneratedQuestion(prompt, type, List.of(), null, dragVariant, content, explanation);
    }

    private List<String> stringList(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        JsonNode arr = node.path(field);
        if (arr.isArray()) {
            arr.forEach(n -> result.add(n.asText()));
        }
        return result;
    }

    private Map<String, String> stringMap(JsonNode node, String field) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        JsonNode obj = node.path(field);
        if (obj.isObject()) {
            obj.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asText()));
        }
        return result;
    }

    private String callLlm(String prompt) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new LlmGenerationException(LlmErrorCause.UNAVAILABLE, true, "LLM_API_KEY is not configured.");
        }

        Map<String, Object> body = Map.of(
                "model", config.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a helpful assistant that always responds with valid JSON only."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.7
        );

        try {
            String raw = restClient.post()
                    .uri(config.getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(raw);
            JsonNode content = node.path("choices").path(0).path("message").path("content");
            String message = content.asText(null);
            if (message == null || message.isBlank()) {
                throw new LlmGenerationException(LlmErrorCause.MALFORMED, true, "The AI service returned an empty response.");
            }
            return stripCodeFences(message);
        } catch (LlmGenerationException e) {
            throw e;
        } catch (RestClientResponseException e) {
            int status = e.getRawStatusCode();
            if (status == 429) {
                throw new LlmGenerationException(LlmErrorCause.QUOTA, true,
                        "The AI service quota or rate limit was reached. Try again shortly.", e);
            }
            throw new LlmGenerationException(LlmErrorCause.UNAVAILABLE, true,
                    "The AI service is temporarily unavailable. Please try again shortly.", e);
        } catch (Exception e) {
            throw classify(e);
        }
    }

    /**
     * Classifies any transport/parse failure into a typed {@link LlmGenerationException}.
     */
    private LlmGenerationException classify(Exception e) {
        if (isTimeout(e)) {
            return new LlmGenerationException(LlmErrorCause.TIMEOUT, true,
                    "The AI service took too long to respond. Please try again.", e);
        }
        if (isNetwork(e)) {
            return new LlmGenerationException(LlmErrorCause.NETWORK, true,
                    "Could not reach the AI service. Check your internet connection and try again.", e);
        }
        return new LlmGenerationException(LlmErrorCause.UNAVAILABLE, true,
                "The AI service is temporarily unavailable. Please try again shortly.", e);
    }

    private boolean isNetwork(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof java.net.ConnectException
                    || (t.getMessage() != null && (t.getMessage().contains("Connection refused")
                        || t.getMessage().contains("connect timed out")
                        || t.getMessage().contains("Unable to resolve")))) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private boolean isTimeout(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SocketTimeoutException || (t.getMessage() != null && t.getMessage().contains("timed out"))) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private LlmGenerationException malformed(String message, Throwable cause) {
        return new LlmGenerationException(LlmErrorCause.MALFORMED, true, message, cause);
    }

    /**
     * Removes markdown code fences if the LLM wrapped JSON in ```json ... ```.
     */
    private String stripCodeFences(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastTriple = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastTriple > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastTriple).trim();
            }
        }
        return trimmed;
    }
}
