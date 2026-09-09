package com.endia.infrastructure.llm;

import com.endia.application.port.LlmProvider;
import com.endia.domain.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible LLM provider implemented with Java 21 RestClient.
 * Zero references to any provider classes leak into services/controllers.
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
            throw new ServiceUnavailableException("Failed to parse LLM subtopic response", e);
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
            return new GeneratedSnack(concept, metaphor, whyItMatters, related);
        } catch (Exception e) {
            throw new ServiceUnavailableException("Failed to parse LLM snack response", e);
        }
    }

    @Override
    public List<GeneratedQuestion> generateQuiz(String theme, String subtopicName, int count) {
        String response = callLlm(templates.generateQuiz(theme, subtopicName, count));
        try {
            JsonNode root = objectMapper.readTree(response);
            List<GeneratedQuestion> result = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode q : root) {
                    String prompt = q.path("prompt").asText(null);
                    int correctIndex = q.path("correctIndex").asInt(-1);
                    String explanation = q.path("explanation").asText(null);
                    List<String> options = new ArrayList<>();
                    if (q.path("options").isArray()) {
                        q.path("options").forEach(o -> options.add(o.asText()));
                    }
                    result.add(new GeneratedQuestion(prompt, options, correctIndex, explanation));
                }
            }
            return result;
        } catch (Exception e) {
            throw new ServiceUnavailableException("Failed to parse LLM quiz response", e);
        }
    }

    private String callLlm(String prompt) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new ServiceUnavailableException("LLM_API_KEY is not configured.");
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
                throw new ServiceUnavailableException("The AI service returned an empty response.");
            }
            return stripCodeFences(message);
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new ServiceUnavailableException("The AI service is temporarily unavailable. Please try again later.", e);
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("The AI service is temporarily unavailable. Please try again later.", e);
        } catch (Exception e) {
            throw new ServiceUnavailableException("The AI service is temporarily unavailable. Please try again later.", e);
        }
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
