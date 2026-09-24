package com.endia.infrastructure.llm;

import com.endia.application.port.LlmProvider;
import com.endia.domain.enums.DragDropVariant;
import com.endia.domain.enums.LlmErrorCause;
import com.endia.domain.exception.LlmGenerationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryingLlmProviderTest {

    @Test
    void succeedsWithinThreeAttempts() {
        AtomicInteger calls = new AtomicInteger();
        LlmProvider delegate = new StubProvider(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new LlmGenerationException(LlmErrorCause.UNAVAILABLE, true, "down");
            }
            return List.of();
        });

        RetryingLlmProvider provider = new RetryingLlmProvider(delegate, 3, 1, 2.0, 10, 0.0);
        List<String> result = provider.suggestSubtopics("Theme");

        assertEquals(0, result.size());
        assertEquals(3, calls.get());
    }

    @Test
    void exhaustsAfterThreeAttempts() {
        AtomicInteger calls = new AtomicInteger();
        LlmProvider delegate = new StubProvider(() -> {
            calls.incrementAndGet();
            throw new LlmGenerationException(LlmErrorCause.TIMEOUT, true, "timeout");
        });

        RetryingLlmProvider provider = new RetryingLlmProvider(delegate, 3, 1, 2.0, 10, 0.0);
        LlmGenerationException ex = assertThrows(LlmGenerationException.class, () -> provider.suggestSubtopics("Theme"));

        assertEquals(LlmErrorCause.TIMEOUT, ex.getCauseType());
        assertEquals(3, calls.get());
    }

    @Test
    void passThroughForNonRetryableCause() {
        AtomicInteger calls = new AtomicInteger();
        LlmProvider delegate = new StubProvider(() -> {
            calls.incrementAndGet();
            throw new LlmGenerationException(LlmErrorCause.UNAVAILABLE, false, "not retryable");
        });

        RetryingLlmProvider provider = new RetryingLlmProvider(delegate, 3, 1, 2.0, 10, 0.0);
        assertThrows(LlmGenerationException.class, () -> provider.generateQuiz("Theme", "Subtopic", 4, DragDropVariant.SEQUENCING));
        assertEquals(1, calls.get());
    }

    @Test
    void retriesExactlyTheConfiguredMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();
        LlmProvider delegate = new StubProvider(() -> {
            calls.incrementAndGet();
            throw new LlmGenerationException(LlmErrorCause.TIMEOUT, true, "timeout");
        });

        RetryingLlmProvider provider = new RetryingLlmProvider(delegate, 5, 1, 2.0, 10, 0.0);
        assertThrows(LlmGenerationException.class, () -> provider.suggestSubtopics("Theme"));
        assertEquals(5, calls.get());
    }

    // ---- Feature 014 (US1): the new port method is wrapped by the same retry decorator (FR-026) ----

    @Test
    void retriesMeaningSnackAndRethrowsTypedCause() {
        AtomicInteger calls = new AtomicInteger();
        LlmProvider delegate = new StubProvider(() -> {
            calls.incrementAndGet();
            throw new LlmGenerationException(LlmErrorCause.UNAVAILABLE, true, "down");
        });

        RetryingLlmProvider provider = new RetryingLlmProvider(delegate, 3, 1, 2.0, 10, 0.0);
        LlmGenerationException ex = assertThrows(LlmGenerationException.class,
                () -> provider.generateMeaningSnack("Theme", "term", "def"));

        assertEquals(LlmErrorCause.UNAVAILABLE, ex.getCauseType());
        assertEquals(3, calls.get());
    }

    @Test
    void meaningSnackSucceedsWithinThreeAttempts() {
        AtomicInteger calls = new AtomicInteger();
        LlmProvider delegate = new StubProvider(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new LlmGenerationException(LlmErrorCause.UNAVAILABLE, true, "down");
            }
            return new LlmProvider.GeneratedMeaningSnack("c", "m", "w", List.of("r"));
        });

        RetryingLlmProvider provider = new RetryingLlmProvider(delegate, 3, 1, 2.0, 10, 0.0);
        LlmProvider.GeneratedMeaningSnack result = provider.generateMeaningSnack("Theme", "term", "def");

        assertEquals("c", result.concept());
        assertEquals(3, calls.get());
    }

    // ---- Feature 016 (US1): the new port method is wrapped by the same retry decorator (FR-005) ----

    @Test
    void retriesDefinitionAndRethrowsTypedCause() {
        AtomicInteger calls = new AtomicInteger();
        LlmProvider delegate = new StubProvider(() -> {
            calls.incrementAndGet();
            throw new LlmGenerationException(LlmErrorCause.UNAVAILABLE, true, "down");
        });

        RetryingLlmProvider provider = new RetryingLlmProvider(delegate, 3, 1, 2.0, 10, 0.0);
        LlmGenerationException ex = assertThrows(LlmGenerationException.class,
                () -> provider.generateDefinition("Theme", "term"));

        assertEquals(LlmErrorCause.UNAVAILABLE, ex.getCauseType());
        assertEquals(3, calls.get());
    }

    @Test
    void definitionSucceedsWithinThreeAttempts() {
        AtomicInteger calls = new AtomicInteger();
        LlmProvider delegate = new StubProvider(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new LlmGenerationException(LlmErrorCause.UNAVAILABLE, true, "down");
            }
            return new LlmProvider.GeneratedDefinition("light to sugar");
        });

        RetryingLlmProvider provider = new RetryingLlmProvider(delegate, 3, 1, 2.0, 10, 0.0);
        LlmProvider.GeneratedDefinition result = provider.generateDefinition("Theme", "term");

        assertEquals("light to sugar", result.definition());
        assertEquals(3, calls.get());
    }

    private static class StubProvider implements LlmProvider {
        private final java.util.function.Supplier<?> supplier;

        StubProvider(java.util.function.Supplier<?> supplier) {
            this.supplier = supplier;
        }

        @Override
        public List<String> suggestSubtopics(String theme) {
            return (List<String>) supplier.get();
        }

        @Override
        public GeneratedSnack generateSnack(String theme, String subtopicName) {
            return (GeneratedSnack) supplier.get();
        }

        @Override
        public List<GeneratedQuestion> generateQuiz(String theme, String subtopicName, int count, DragDropVariant variant) {
            return (List<GeneratedQuestion>) supplier.get();
        }

        @Override
        public GeneratedMeaningSnack generateMeaningSnack(String theme, String term, String definition) {
            return (GeneratedMeaningSnack) supplier.get();
        }

        @Override
        public GeneratedDefinition generateDefinition(String theme, String term) {
            return (GeneratedDefinition) supplier.get();
        }
    }
}
