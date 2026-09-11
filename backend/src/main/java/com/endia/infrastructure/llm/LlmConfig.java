package com.endia.infrastructure.llm;

import com.endia.application.port.LlmProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configures the LLM provider beans from environment variables, plus the retry
 * decorator and the bounded batch executor used by the asynchronous batch worker.
 */
@Configuration
public class LlmConfig {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final int retryMaxAttempts;
    private final long retryInitialBackoffMs;
    private final double retryBackoffFactor;
    private final long retryMaxBackoffMs;
    private final double retryJitterRatio;

    public LlmConfig(
            @Value("${llm.api-key:}") String apiKey,
            @Value("${llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${llm.model:gpt-4o-mini}") String model,
            @Value("${llm.timeout-seconds:30}") int timeoutSeconds,
            @Value("${batch.retry.max-attempts:3}") int retryMaxAttempts,
            @Value("${batch.retry.initial-backoff-ms:1000}") long retryInitialBackoffMs,
            @Value("${batch.retry.backoff-factor:2.0}") double retryBackoffFactor,
            @Value("${batch.retry.max-backoff-ms:10000}") long retryMaxBackoffMs,
            @Value("${batch.retry.jitter-ratio:0.2}") double retryJitterRatio
    ) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryInitialBackoffMs = retryInitialBackoffMs;
        this.retryBackoffFactor = retryBackoffFactor;
        this.retryMaxBackoffMs = retryMaxBackoffMs;
        this.retryJitterRatio = retryJitterRatio;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Builds a RestClient with connect/read timeouts configured from llm.timeout-seconds.
     */
    @Bean
    public RestClient llmRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * Bounded thread pool for the asynchronous batch worker (snacks sequenced,
     * quizzes in parallel). Prevents the default unbounded single-thread executor
     * from starving tomcat worker threads.
     */
    @Bean(name = "batchExecutor")
    public TaskExecutor batchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("batch-worker-");
        executor.initialize();
        return executor;
    }

    /**
     * Separate pool for nested per-subtopic quiz prefetch. Keeping it distinct from
     * {@code batchExecutor} removes the cyclic wait that deadlocked the batch worker
     * (FR-004). {@link ThreadPoolExecutor.CallerRunsPolicy} is the liveness valve: if the
     * quiz pool and queue saturate, the submitting batch worker executes the quiz itself,
     * so the bounded join can always complete and the batch always reaches a terminal status.
     */
    @Bean(name = "quizExecutor")
    public TaskExecutor quizExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("quiz-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Wraps the concrete {@link OpenAiLlmProvider} with the retry decorator so every
     * port method (snack AND quiz AND subtopics) auto-retries transient failures.
     * Services/controllers depend on this primary {@link LlmProvider} bean.
     */
    @Bean
    @Primary
    public LlmProvider retryingLlmProvider(OpenAiLlmProvider delegate) {
        return new RetryingLlmProvider(delegate, retryMaxAttempts, retryInitialBackoffMs,
                retryBackoffFactor, retryMaxBackoffMs, retryJitterRatio);
    }
}
