package com.endia.infrastructure.llm;

import com.endia.application.port.LlmProvider;
import com.endia.domain.enums.DragDropVariant;
import com.endia.domain.enums.LlmErrorCause;
import com.endia.domain.exception.LlmGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiLlmProviderTest {

    private MockWebServer server;
    private OpenAiLlmProvider provider;
    private LlmPromptTemplates templates = new LlmPromptTemplates();
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder().requestFactory(factory).build();

        LlmConfig config = new LlmConfig(
                "test-api-key",
                server.url("/v1").toString(),
                "gpt-4o-mini",
                5,
                3, 1000, 2.0, 10000, 0.2);

        provider = new OpenAiLlmProvider(restClient, config, templates);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private LlmConfig config(String apiKey, int timeoutSeconds, String baseUrl) {
        return new LlmConfig(apiKey, baseUrl, "gpt-4o-mini", timeoutSeconds, 3, 1000, 2.0, 10000, 0.2);
    }

    @Test
    void suggestSubtopicsParsesJsonArray() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"choices":[{"message":{"content":"[\\"Light reactions\\",\\"Calvin cycle\\",\\"Chloroplast\\"]"}}]}}
                        """));

        List<String> result = provider.suggestSubtopics("Photosynthesis");

        assertEquals(3, result.size());
        assertEquals("Light reactions", result.get(0));

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().endsWith("/chat/completions"));
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"));
    }

    @Test
    void suggestSubtopicsStripsCodeFences() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"choices":[{"message":{"content":"```json\\n[\\"A\\",\\"B\\"]\\n```"}}]}}
                        """));

        List<String> result = provider.suggestSubtopics("Theme");

        assertEquals(2, result.size());
        assertEquals("A", result.get(0));
    }

    @Test
    void generateSnackParsesObject() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"choices":[{"message":{"content":"{\\"concept\\":\\"c\\",\\"metaphor\\":\\"m\\",\\"whyItMatters\\":\\"w\\",\\"related\\":[\\"r1\\",\\"r2\\"]}"}}]}}
                        """));

        LlmProvider.GeneratedSnack snack = provider.generateSnack("Theme", "Subtopic");

        assertEquals("c", snack.concept());
        assertEquals("m", snack.metaphor());
        assertEquals("w", snack.whyItMatters());
        assertEquals(2, snack.related().size());
    }

    @Test
    void generateQuizParsesArray() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"choices":[{"message":{"content":"[{\\"prompt\\":\\"Q1\\",\\"options\\":[\\"A\\",\\"B\\",\\"C\\",\\"D\\"],\\"correctIndex\\":2,\\"explanation\\":\\"E\\"}]"}}]}}
                        """));

        List<LlmProvider.GeneratedQuestion> questions = provider.generateQuiz("Theme", "Subtopic", 4, DragDropVariant.SEQUENCING);

        assertEquals(1, questions.size());
        assertEquals("Q1", questions.get(0).prompt());
        assertEquals(2, questions.get(0).correctIndex());
        assertEquals(4, questions.get(0).options().size());
    }

    @Test
    void generateQuizParsesDragDrop() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"choices":[{"message":{"content":"[{\\"type\\":\\"DRAG_DROP\\",\\"prompt\\":\\"Order\\",\\"explanation\\":\\"E\\",\\"variant\\":\\"SEQUENCING\\",\\"dragDrop\\":{\\"items\\":[\\"A\\",\\"B\\"],\\"correctOrder\\":[\\"A\\",\\"B\\"]}}]"}}]}}
                        """));

        List<LlmProvider.GeneratedQuestion> questions = provider.generateQuiz("Theme", "Subtopic", 4, DragDropVariant.SEQUENCING);

        assertEquals(1, questions.size());
        assertEquals(LlmProvider.GeneratedQuestion.QuestionType.DRAG_DROP, questions.get(0).type());
        assertEquals(DragDropVariant.SEQUENCING, questions.get(0).variant());
        assertEquals(List.of("A", "B"), questions.get(0).dragDropContent().correctOrder());
    }

    // ---- Cause mapping (T072) ----

    @Test
    void throwsUnauthorizedCauseOnQuota() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("rate limited"));

        LlmGenerationException ex = assertThrows(LlmGenerationException.class, () -> provider.suggestSubtopics("Theme"));
        assertEquals(LlmErrorCause.QUOTA, ex.getCauseType());
        assertEquals(429, ex.getStatus());
        assertTrue(ex.isRetryable());
    }

    @Test
    void throwsUnavailableCauseOnServerError() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("unavailable"));

        LlmGenerationException ex = assertThrows(LlmGenerationException.class, () -> provider.suggestSubtopics("Theme"));
        assertEquals(LlmErrorCause.UNAVAILABLE, ex.getCauseType());
        assertEquals(503, ex.getStatus());
        assertTrue(ex.isRetryable());
    }

    @Test
    void throwsTimeoutCauseOnReadTimeout() {
        server.enqueue(new MockResponse().setBodyDelay(2, TimeUnit.SECONDS).setBody("{}"));
        // Read timeout of 500ms triggers a ResourceAccessException that carries "timed out".
        OpenAiLlmProvider fastProvider = new OpenAiLlmProvider(
                RestClient.builder().requestFactory(factory(1)).build(),
                config("test-api-key", 1, server.url("/v1").toString()),
                templates);

        LlmGenerationException ex = assertThrows(LlmGenerationException.class, () -> fastProvider.suggestSubtopics("Theme"));
        assertEquals(LlmErrorCause.TIMEOUT, ex.getCauseType());
        assertTrue(ex.isRetryable());
    }

    @Test
    void networkCauseIsMappedWhenConnectRefused() {
        OpenAiLlmProvider p = new OpenAiLlmProvider(
                RestClient.builder().requestFactory(factory(2)).build(),
                config("test-api-key", 2, "http://localhost:1/v1"),
                templates);
        LlmGenerationException ex = assertThrows(LlmGenerationException.class, () -> p.suggestSubtopics("Theme"));
        assertEquals(LlmErrorCause.NETWORK, ex.getCauseType());
        assertTrue(ex.isRetryable());
    }

    @Test
    void throwsMalformedCauseOnUnparsableResponse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"choices":[{"message":{"content":"not-json-at-all"}}]}}
                        """));

        LlmGenerationException ex = assertThrows(LlmGenerationException.class, () -> provider.suggestSubtopics("Theme"));
        assertEquals(LlmErrorCause.MALFORMED, ex.getCauseType());
        assertEquals(400, ex.getStatus());
        assertTrue(ex.isRetryable());
    }

    @Test
    void throwsUnavailableCauseWhenApiKeyMissing() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        OpenAiLlmProvider noKeyProvider = new OpenAiLlmProvider(restClient, config("", 5, server.url("/v1").toString()), templates);

        LlmGenerationException ex = assertThrows(LlmGenerationException.class, () -> noKeyProvider.suggestSubtopics("Theme"));
        assertEquals(LlmErrorCause.UNAVAILABLE, ex.getCauseType());
    }

    private SimpleClientHttpRequestFactory factory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofMillis(500));
        return factory;
    }
}
