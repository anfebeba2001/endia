package com.endia.infrastructure.llm;

import com.endia.application.port.LlmProvider;
import com.endia.domain.enums.DragDropVariant;
import com.endia.domain.exception.LlmGenerationException;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Infrastructure decorator around {@link LlmProvider} that auto-retries transient
 * {@link LlmGenerationException}s up to {@code maxAttempts} total attempts with
 * exponential backoff + jitter (FR-016/018). Re-throws the final typed exception
 * when the budget is exhausted. Wraps every port method (snack AND quiz AND subtopics).
 */
public class RetryingLlmProvider implements LlmProvider {

    private final LlmProvider delegate;
    private final int maxAttempts;
    private final long initialBackoffMs;
    private final double backoffFactor;
    private final long maxBackoffMs;
    private final double jitterRatio;

    public RetryingLlmProvider(LlmProvider delegate, int maxAttempts, long initialBackoffMs,
                               double backoffFactor, long maxBackoffMs, double jitterRatio) {
        this.delegate = delegate;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMs = initialBackoffMs;
        this.backoffFactor = backoffFactor;
        this.maxBackoffMs = maxBackoffMs;
        this.jitterRatio = jitterRatio;
    }

    @Override
    public List<String> suggestSubtopics(String theme) {
        return retry(() -> delegate.suggestSubtopics(theme));
    }

    @Override
    public GeneratedSnack generateSnack(String theme, String subtopicName) {
        return retry(() -> delegate.generateSnack(theme, subtopicName));
    }

    @Override
    public List<GeneratedQuestion> generateQuiz(String theme, String subtopicName, int count, DragDropVariant variant) {
        return retry(() -> delegate.generateQuiz(theme, subtopicName, count, variant));
    }

    @Override
    public GeneratedMeaningSnack generateMeaningSnack(String theme, String term, String definition) {
        return retry(() -> delegate.generateMeaningSnack(theme, term, definition));
    }

    private <T> T retry(ThrowingSupplier<T> supplier) {
        LlmGenerationException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (LlmGenerationException e) {
                last = e;
                if (!e.isRetryable() || attempt >= maxAttempts) {
                    throw e;
                }
                sleep(backoff(attempt));
            }
        }
        throw last;
    }

    private long backoff(int attempt) {
        double base = Math.min(maxBackoffMs, initialBackoffMs * Math.pow(backoffFactor, attempt - 1));
        double jitter = base * jitterRatio;
        return Math.round(base + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * jitter);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LlmGenerationException(com.endia.domain.enums.LlmErrorCause.UNAVAILABLE, false, "Interrupted during retry backoff.", ie);
        }
    }

    private interface ThrowingSupplier<T> {
        T get();
    }
}
