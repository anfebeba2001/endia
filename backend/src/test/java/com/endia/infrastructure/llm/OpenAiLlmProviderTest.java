package com.endia.infrastructure.llm;

import com.endia.application.port.LlmProvider;
import com.endia.domain.exception.ServiceUnavailableException;
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
import java.util.Map;

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
                5);

        provider = new OpenAiLlmProvider(restClient, config, templates);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void suggestSubtopicsParsesJsonArray() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"choices":[{"message":{"content":"[\\"Light reactions\\",\\"Calvin cycle\\",\\"Chloroplast\\"]"}}]}
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

        List<LlmProvider.GeneratedQuestion> questions = provider.generateQuiz("Theme", "Subtopic", 4);

        assertEquals(1, questions.size());
        assertEquals("Q1", questions.get(0).prompt());
        assertEquals(2, questions.get(0).correctIndex());
        assertEquals(4, questions.get(0).options().size());
    }

    @Test
    void throwsServiceUnavailableOnServerError() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("unavailable"));

        assertThrows(ServiceUnavailableException.class, () -> provider.suggestSubtopics("Theme"));
    }

    @Test
    void throwsServiceUnavailableWhenApiKeyMissing() throws Exception {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        LlmConfig noKeyConfig = new LlmConfig("", server.url("/v1").toString(), "gpt-4o-mini", 5);
        OpenAiLlmProvider noKeyProvider = new OpenAiLlmProvider(restClient, noKeyConfig, templates);

        assertThrows(ServiceUnavailableException.class, () -> noKeyProvider.suggestSubtopics("Theme"));
    }
}
