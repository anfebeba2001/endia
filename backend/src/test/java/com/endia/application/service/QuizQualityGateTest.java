package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizQualityGateTest {

    private QuizQualityGate gate;

    @BeforeEach
    void setUp() {
        gate = new QuizQualityGate();
    }

    private LlmProvider.GeneratedQuestion question(String prompt, List<String> options, int correctIndex, String explanation) {
        return new LlmProvider.GeneratedQuestion(prompt, options, correctIndex, explanation);
    }

    @Test
    void validQuestionPasses() {
        var q = question("What is the primary role of Photosystem II?",
                List.of("Fixing CO2 into glucose", "Absorbing light to split water", "Producing NADPH", "Converting glucose to energy"),
                1, "Photosystem II absorbs light to split water.");
        assertTrue(gate.validate(q));
    }

    @Test
    void duplicateOptionsRejected() {
        var q = question("What is a triangle?",
                List.of("Three sided shape", "Three sided shape", "Four sided shape", "Round shape"),
                0, "A triangle has three sides.");
        assertFalse(gate.validate(q));
    }

    @Test
    void caseInsensitiveDuplicateDetected() {
        var q = question("What is a triangle?",
                List.of("Three sided shape", "three sided shape", "Four sided shape", "Round shape"),
                0, "A triangle has three sides.");
        assertFalse(gate.validate(q));
    }

    @Test
    void emptyOptionRejected() {
        var q = question("What is a triangle?",
                List.of("Three sided shape", "", "Four sided shape", "Round shape"),
                0, "A triangle has three sides.");
        assertFalse(gate.validate(q));
    }

    @Test
    void shortOptionRejected() {
        var q = question("What is a triangle?",
                List.of("Three sided shape", "Sides", "Four sided shape", "Round shape"),
                0, "A triangle has three sides.");
        assertFalse(gate.validate(q));
    }

    @Test
    void fewerThanFourOptionsRejected() {
        var q = question("What is a triangle?",
                List.of("Three sided shape", "Four sided shape", "Round shape"),
                0, "A triangle has three sides.");
        assertFalse(gate.validate(q));
    }

    @Test
    void moreThanFourOptionsRejected() {
        var q = question("What is a triangle?",
                List.of("A", "B", "C", "D", "E"),
                0, "Should be rejected.");
        assertFalse(gate.validate(q));
    }

    @Test
    void nullOptionsRejected() {
        var q = question("What is a triangle?", null, 0, "Explanation");
        assertFalse(gate.validate(q));
    }

    @Test
    void outOfRangeCorrectIndexRejected() {
        var q = question("What is a triangle?",
                List.of("Three sided shape", "Four sided shape", "Round shape", "Square shape"),
                7, "A triangle has three sides.");
        assertFalse(gate.validate(q));
    }

    @Test
    void firstFailureReasonReturnsDetail() {
        var q = question("What is a triangle?",
                List.of("Three sided shape", "", "Four sided shape", "Round shape"),
                0, "A triangle has three sides.");
        String reason = gate.firstFailureReason(q);
        assertNotNull(reason);
        assertTrue(reason.toLowerCase().contains("short"));
    }

    @Test
    void firstFailureReasonNullWhenValid() {
        var q = question("What is the primary role of Photosystem II?",
                List.of("Fixing CO2 into glucose", "Absorbing light to split water", "Producing NADPH", "Converting glucose to energy"),
                1, "Explanation");
        assertNull(gate.firstFailureReason(q));
    }

    @Test
    void optionsTooDissimilarInLengthRejected() {
        var q = question("What is a triangle?",
                List.of("A very long option that is quite detailed and verbose", "Short", "Four sided shape", "Round shape"),
                0, "Explanation");
        assertFalse(gate.validate(q));
    }
}
