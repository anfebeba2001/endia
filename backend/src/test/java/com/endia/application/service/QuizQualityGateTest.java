package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.enums.DragDropVariant;
import com.endia.domain.model.DragDropContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        return new LlmProvider.GeneratedQuestion(prompt,
                LlmProvider.GeneratedQuestion.QuestionType.MULTIPLE_CHOICE,
                options, correctIndex, null, null, explanation);
    }

    private LlmProvider.GeneratedQuestion dragDrop(String prompt, DragDropContent content, String explanation) {
        return new LlmProvider.GeneratedQuestion(prompt,
                LlmProvider.GeneratedQuestion.QuestionType.DRAG_DROP,
                List.of(), null, content.variant(), content, explanation);
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

    // ---- T068: drag-drop validation rules ----

    @Test
    void validDragDropSequencingPasses() {
        var content = new DragDropContent(DragDropVariant.SEQUENCING,
                List.of("A", "B", "C"), List.of(), Map.of(), List.of("A", "B", "C"));
        assertTrue(gate.validate(dragDrop("Order them.", content, "Explanation.")));
    }

    @Test
    void emptyDragDropItemsRejected() {
        var content = new DragDropContent(DragDropVariant.SEQUENCING,
                List.of(), List.of(), Map.of(), List.of());
        assertFalse(gate.validate(dragDrop("Order them.", content, "Explanation.")));
    }

    @Test
    void duplicateDragDropItemsRejected() {
        var content = new DragDropContent(DragDropVariant.SEQUENCING,
                List.of("A", "A", "C"), List.of(), Map.of(), List.of("A", "A", "C"));
        assertFalse(gate.validate(dragDrop("Order them.", content, "Explanation.")));
    }

    @Test
    void incompleteCorrectOrderRejected() {
        var content = new DragDropContent(DragDropVariant.SEQUENCING,
                List.of("A", "B", "C"), List.of(), Map.of(), List.of("A", "B"));
        assertFalse(gate.validate(dragDrop("Order them.", content, "Explanation.")));
    }

    @Test
    void badVariantNullRejected() {
        var content = new DragDropContent(null, List.of("A", "B"), List.of(), Map.of(), List.of("A", "B"));
        assertFalse(gate.validate(dragDrop("Order them.", content, "Explanation.")));
    }

    @Test
    void classificationRequiresEveryItemCategorized() {
        var content = new DragDropContent(DragDropVariant.CLASSIFICATION,
                List.of("Earth", "Mars", "Jupiter"), List.of("Rocky", "Gas"),
                Map.of("Earth", "Rocky", "Mars", "Rocky"), List.of());
        assertFalse(gate.validate(dragDrop("Classify.", content, "Explanation.")));
    }

    @Test
    void classificationCategoriesEmptyRejected() {
        var content = new DragDropContent(DragDropVariant.CLASSIFICATION,
                List.of("Earth", "Mars"), List.of(), Map.of("Earth", "X", "Mars", "X"), List.of());
        assertFalse(gate.validate(dragDrop("Classify.", content, "Explanation.")));
    }

    @Test
    void matchingRequiresEveryLeftKeyHasValue() {
        var content = new DragDropContent(DragDropVariant.MATCHING,
                List.of("H2O", "CO2", "ATP"), List.of(), Map.of("H2O", "donor", "CO2", "source"), List.of());
        assertFalse(gate.validate(dragDrop("Match.", content, "Explanation.")));
    }

    @Test
    void dragDropFirstFailureReasonDetail() {
        var content = new DragDropContent(DragDropVariant.SEQUENCING,
                List.of("A", "B"), List.of(), Map.of(), List.of("A"));
        String reason = gate.firstFailureReason(dragDrop("Order.", content, "Explanation."));
        assertNotNull(reason);
    }
}
