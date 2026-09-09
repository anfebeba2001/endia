package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.enums.DragDropVariant;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.InvalidOptionIndexException;
import com.endia.domain.exception.QuizAlreadyAnsweredException;
import com.endia.domain.model.AnswerAttempt;
import com.endia.domain.model.DragDropContent;
import com.endia.domain.model.QuizQuestion;
import com.endia.domain.model.Snack;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaAnswerAttemptRepository;
import com.endia.infrastructure.persistence.JpaQuizQuestionRepository;
import com.endia.infrastructure.persistence.JpaSnackRepository;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private LlmProvider llmProvider;
    @Mock
    private QuizQualityGate qualityGate;
    @Mock
    private JpaSubtopicRepository subtopicRepository;
    @Mock
    private JpaQuizQuestionRepository quizQuestionRepository;
    @Mock
    private JpaAnswerAttemptRepository answerAttemptRepository;
    @Mock
    private JpaSnackRepository snackRepository;

    private QuizService quizService;

    private Theme theme;
    private Subtopic subtopic;
    private UUID subtopicId;
    private UUID questionId;

    @BeforeEach
    void setUp() {
        quizService = new QuizService(llmProvider, qualityGate, subtopicRepository,
                quizQuestionRepository, answerAttemptRepository, snackRepository);
        theme = new Theme("Photosynthesis");
        subtopicId = UUID.randomUUID();
        subtopic = new Subtopic(theme, "Light reactions");
        subtopic.setId(subtopicId);
        questionId = UUID.randomUUID();
    }

    private LlmProvider.GeneratedQuestion validQuestion(String prompt, int correctIndex) {
        return new LlmProvider.GeneratedQuestion(prompt,
                LlmProvider.GeneratedQuestion.QuestionType.MULTIPLE_CHOICE,
                List.of("Option one here", "Option two here", "Option three here", "Option four here"),
                correctIndex, null, null, "This is the explanation.");
    }

    @Test
    void generateQuizPersistsQuestionsAndStripsSensitiveDataViaResponseMapping() {
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        when(qualityGate.validate(any(LlmProvider.GeneratedQuestion.class))).thenReturn(true);
        when(llmProvider.generateQuiz(any(), any(), anyInt(), any(DragDropVariant.class))).thenReturn(List.of(
                validQuestion("Question 1?", 0),
                validQuestion("Question 2?", 1),
                validQuestion("Question 3?", 2),
                validQuestion("Question 4?", 3)
        ));
        when(quizQuestionRepository.save(any(QuizQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        List<QuizQuestion> questions = quizService.generateQuiz(subtopicId, 4);

        assertEquals(4, questions.size());
        for (QuizQuestion q : questions) {
            assertEquals(4, q.getOptions().size());
            assertTrue(q.getCorrectIndex() != null && q.getCorrectIndex() >= 0 && q.getCorrectIndex() < 4);
        }
        ArgumentCaptor<QuizQuestion> captor = ArgumentCaptor.forClass(QuizQuestion.class);
        verify(quizQuestionRepository, org.mockito.Mockito.times(4)).save(captor.capture());
    }

    @Test
    void generateQuizThrows404WhenSubtopicNotFound() {
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> quizService.generateQuiz(subtopicId, 4));
    }

    @Test
    void answerCorrectQuestionReturnsXp10AndPersistsAttempt() {
        QuizQuestion question = new QuizQuestion(subtopic, "Question?", List.of("A", "B", "C", "D"), 2, "Expl");
        question.setId(questionId);

        when(quizQuestionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(answerAttemptRepository.existsByQuizQuestionId(questionId)).thenReturn(false);
        when(answerAttemptRepository.save(any(AnswerAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizService.AnswerResult result = quizService.answerQuestion(questionId, 2);

        assertTrue(result.correct());
        assertEquals(2, result.correctIndex());
        assertEquals(10, result.xpEarned());

        ArgumentCaptor<AnswerAttempt> captor = ArgumentCaptor.forClass(AnswerAttempt.class);
        verify(answerAttemptRepository).save(captor.capture());
        assertTrue(captor.getValue().isCorrect());
        assertEquals(10, captor.getValue().getXpEarned());
    }

    @Test
    void answerWrongQuestionReturnsXp0() {
        QuizQuestion question = new QuizQuestion(subtopic, "Question?", List.of("A", "B", "C", "D"), 2, "Expl");
        question.setId(questionId);

        when(quizQuestionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(answerAttemptRepository.existsByQuizQuestionId(questionId)).thenReturn(false);
        when(answerAttemptRepository.save(any(AnswerAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizService.AnswerResult result = quizService.answerQuestion(questionId, 0);

        assertFalse(result.correct());
        assertEquals(0, result.xpEarned());
    }

    @Test
    void answerAlreadyAnsweredThrowsConflict() {
        QuizQuestion question = new QuizQuestion(subtopic, "Question?", List.of("A", "B", "C", "D"), 2, "Expl");
        question.setId(questionId);

        when(quizQuestionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(answerAttemptRepository.existsByQuizQuestionId(questionId)).thenReturn(true);

        assertThrows(QuizAlreadyAnsweredException.class, () -> quizService.answerQuestion(questionId, 1));
    }

    @Test
    void answerOutOfRangeIndexThrowsInvalid() {
        QuizQuestion question = new QuizQuestion(subtopic, "Question?", List.of("A", "B", "C", "D"), 2, "Expl");
        question.setId(questionId);

        when(quizQuestionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(answerAttemptRepository.existsByQuizQuestionId(questionId)).thenReturn(false);

        assertThrows(InvalidOptionIndexException.class, () -> quizService.answerQuestion(questionId, 4));
    }

    @Test
    void answerQuestionNotFoundThrows404() {
        when(quizQuestionRepository.findById(questionId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> quizService.answerQuestion(questionId, 0));
    }

    // ---- Re-roll tests ----

    @Test
    void rerollTriggersWhenFirstQuestionFailsQualityGate() {
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        when(llmProvider.generateQuiz(any(), any(), anyInt(), any(DragDropVariant.class)))
                .thenReturn(List.of(validQuestion("Bad?", 0)))
                .thenReturn(List.of(validQuestion("Good?", 1)));
        when(qualityGate.validate(any(LlmProvider.GeneratedQuestion.class))).thenReturn(false, true);
        when(quizQuestionRepository.save(any(QuizQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        List<QuizQuestion> questions = quizService.generateQuiz(subtopicId, 4);

        assertEquals(1, questions.size());
        assertEquals("Good?", questions.get(0).getPrompt());
    }

    @Test
    void qualityGateRejectsAllIdenticalOptionsAndServesBestResult() {
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        LlmProvider.GeneratedQuestion bad = new LlmProvider.GeneratedQuestion(
                "Question?", LlmProvider.GeneratedQuestion.QuestionType.MULTIPLE_CHOICE,
                List.of("Same option text", "Same option text", "Same option text", "Same option text"),
                0, null, null, "Expl");
        LlmProvider.GeneratedQuestion good = validQuestion("Good?", 0);
        when(llmProvider.generateQuiz(any(), any(), anyInt(), any(DragDropVariant.class)))
                .thenReturn(List.of(bad))
                .thenReturn(List.of(good));
        when(qualityGate.validate(any(LlmProvider.GeneratedQuestion.class))).thenReturn(false, true);
        when(quizQuestionRepository.save(any(QuizQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        List<QuizQuestion> questions = quizService.generateQuiz(subtopicId, 4);

        assertEquals(1, questions.size());
        assertEquals(4, questions.get(0).getOptions().size());
    }

    @Test
    void secondFailureLogsWarningAndServesBestAvailable() {
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        LlmProvider.GeneratedQuestion bad = new LlmProvider.GeneratedQuestion(
                "Question?", LlmProvider.GeneratedQuestion.QuestionType.MULTIPLE_CHOICE,
                List.of("Same", "Same", "Same", "Same"), 0, null, null, "Expl");
        when(llmProvider.generateQuiz(any(), any(), anyInt(), any(DragDropVariant.class)))
                .thenReturn(List.of(bad))
                .thenReturn(List.of(bad));
        when(qualityGate.validate(any(LlmProvider.GeneratedQuestion.class))).thenReturn(false, false);
        when(quizQuestionRepository.save(any(QuizQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        List<QuizQuestion> questions = quizService.generateQuiz(subtopicId, 4);

        assertEquals(1, questions.size());
    }

    // ---- XP calculation tests (T034) ----

    private QuizQuestion persistedQuestion(UUID id, int correctIndex) {
        QuizQuestion q = new QuizQuestion(subtopic, "Question " + id, List.of("A", "B", "C", "D"), correctIndex, "Expl");
        q.setId(id);
        return q;
    }

    @Test
    void xpCalculationOneOfFourCorrectGivesTen() {
        UUID q1 = UUID.randomUUID(), q2 = UUID.randomUUID(), q3 = UUID.randomUUID(), q4 = UUID.randomUUID();
        when(quizQuestionRepository.findBySubtopicId(subtopicId))
                .thenReturn(List.of(persistedQuestion(q1, 0), persistedQuestion(q2, 0), persistedQuestion(q3, 0), persistedQuestion(q4, 0)));
        when(answerAttemptRepository.findByQuizQuestionId(q1)).thenReturn(List.of(new AnswerAttempt(persistedQuestion(q1, 0), 0, true, 10)));
        when(answerAttemptRepository.findByQuizQuestionId(q2)).thenReturn(List.of(new AnswerAttempt(persistedQuestion(q2, 0), 1, false, 0)));
        when(answerAttemptRepository.findByQuizQuestionId(q3)).thenReturn(List.of(new AnswerAttempt(persistedQuestion(q3, 0), 1, false, 0)));
        when(answerAttemptRepository.findByQuizQuestionId(q4)).thenReturn(List.of(new AnswerAttempt(persistedQuestion(q4, 0), 1, false, 0)));

        assertEquals(10, quizService.calculateQuizXp(subtopicId));
    }

    @Test
    void xpCalculationPerfectQuizGivesNinety() {
        UUID q1 = UUID.randomUUID(), q2 = UUID.randomUUID(), q3 = UUID.randomUUID(), q4 = UUID.randomUUID();
        when(quizQuestionRepository.findBySubtopicId(subtopicId))
                .thenReturn(List.of(persistedQuestion(q1, 0), persistedQuestion(q2, 0), persistedQuestion(q3, 0), persistedQuestion(q4, 0)));
        for (UUID id : List.of(q1, q2, q3, q4)) {
            when(answerAttemptRepository.findByQuizQuestionId(id)).thenReturn(List.of(new AnswerAttempt(persistedQuestion(id, 0), 0, true, 10)));
        }

        assertEquals(90, quizService.calculateQuizXp(subtopicId));
    }

    @Test
    void xpCalculationAllWrongGivesZero() {
        UUID q1 = UUID.randomUUID(), q2 = UUID.randomUUID(), q3 = UUID.randomUUID(), q4 = UUID.randomUUID();
        when(quizQuestionRepository.findBySubtopicId(subtopicId))
                .thenReturn(List.of(persistedQuestion(q1, 0), persistedQuestion(q2, 1), persistedQuestion(q3, 2), persistedQuestion(q4, 3)));
        when(answerAttemptRepository.findByQuizQuestionId(q1)).thenReturn(List.of(new AnswerAttempt(persistedQuestion(q1, 0), 1, false, 0)));
        when(answerAttemptRepository.findByQuizQuestionId(q2)).thenReturn(List.of(new AnswerAttempt(persistedQuestion(q2, 1), 2, false, 0)));
        when(answerAttemptRepository.findByQuizQuestionId(q3)).thenReturn(List.of(new AnswerAttempt(persistedQuestion(q3, 2), 3, false, 0)));
        when(answerAttemptRepository.findByQuizQuestionId(q4)).thenReturn(List.of(new AnswerAttempt(persistedQuestion(q4, 3), 0, false, 0)));

        assertEquals(0, quizService.calculateQuizXp(subtopicId));
    }

    // ---- T067: variant selection, mixed generation, drag-drop grading, recap, XP parity ----

    @Test
    void mixedGenerationPersistsMcqAndDragDrop() {
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        when(qualityGate.validate(any(LlmProvider.GeneratedQuestion.class))).thenReturn(true);
        LlmProvider.GeneratedQuestion mcq = validQuestion("Which?", 0);
        DragDropContent content = new DragDropContent(DragDropVariant.SEQUENCING,
                List.of("A", "B", "C"), List.of(), Map.of(), List.of("A", "B", "C"));
        LlmProvider.GeneratedQuestion dd = new LlmProvider.GeneratedQuestion(
                "Order them.", LlmProvider.GeneratedQuestion.QuestionType.DRAG_DROP,
                List.of(), null, DragDropVariant.SEQUENCING, content, "Explanation.");
        when(llmProvider.generateQuiz(any(), any(), anyInt(), any(DragDropVariant.class))).thenReturn(List.of(mcq, dd));
        when(quizQuestionRepository.save(any(QuizQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        List<QuizQuestion> questions = quizService.generateQuiz(subtopicId, 4);

        assertEquals(2, questions.size());
        QuizQuestion persistedDd = questions.get(1);
        assertEquals(com.endia.domain.enums.QuestionType.DRAG_DROP, persistedDd.getType());
        assertNull(persistedDd.getCorrectIndex());
        assertEquals(DragDropContent.class, persistedDd.getDragDropContent().getClass());
    }

    @Test
    void dragDropSequencingGrading() {
        DragDropContent content = new DragDropContent(DragDropVariant.SEQUENCING,
                List.of("A", "B", "C"), List.of(), Map.of(), List.of("A", "B", "C"));
        QuizQuestion q = new QuizQuestion(subtopic, "Order them.", DragDropVariant.SEQUENCING, content, "Exp");
        q.setId(questionId);
        when(quizQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));
        when(answerAttemptRepository.existsByQuizQuestionId(questionId)).thenReturn(false);
        when(answerAttemptRepository.save(any(AnswerAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizService.AnswerResult ok = quizService.answerQuestion(questionId, null, List.of("A", "B", "C"));
        assertTrue(ok.correct());
        assertEquals(10, ok.xpEarned());

        QuizService.AnswerResult bad = quizService.answerQuestion(questionId, null, List.of("C", "A", "B"));
        assertFalse(bad.correct());
        assertEquals(0, bad.xpEarned());
    }

    @Test
    void dragDropClassificationGrading() {
        DragDropContent content = new DragDropContent(DragDropVariant.CLASSIFICATION,
                List.of("Earth", "Mars", "Jupiter"), List.of("Rocky", "Gas"), Map.of("Earth", "Rocky", "Mars", "Rocky", "Jupiter", "Gas"), List.of());
        QuizQuestion q = new QuizQuestion(subtopic, "Classify them.", DragDropVariant.CLASSIFICATION, content, "Exp");
        q.setId(questionId);
        when(quizQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));
        when(answerAttemptRepository.existsByQuizQuestionId(questionId)).thenReturn(false);
        when(answerAttemptRepository.save(any(AnswerAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizService.AnswerResult ok = quizService.answerQuestion(questionId, null, List.of("Earth:Rocky", "Mars:Rocky", "Jupiter:Gas"));
        assertTrue(ok.correct());

        QuizService.AnswerResult bad = quizService.answerQuestion(questionId, null, List.of("Earth:Gas", "Mars:Rocky", "Jupiter:Gas"));
        assertFalse(bad.correct());
    }

    @Test
    void dragDropMatchingGrading() {
        DragDropContent content = new DragDropContent(DragDropVariant.MATCHING,
                List.of("H2O", "CO2", "ATP"), List.of(), Map.of("H2O", "donor", "CO2", "source", "ATP", "currency"), List.of());
        QuizQuestion q = new QuizQuestion(subtopic, "Match them.", DragDropVariant.MATCHING, content, "Exp");
        q.setId(questionId);
        when(quizQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));
        when(answerAttemptRepository.existsByQuizQuestionId(questionId)).thenReturn(false);
        when(answerAttemptRepository.save(any(AnswerAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizService.AnswerResult ok = quizService.answerQuestion(questionId, null, List.of("H2O:donor", "CO2:source", "ATP:currency"));
        assertTrue(ok.correct());
    }

    @Test
    void summaryIncludesRecapAndDragDropBreakdown() {
        DragDropContent content = new DragDropContent(DragDropVariant.SEQUENCING,
                List.of("A", "B"), List.of(), Map.of(), List.of("A", "B"));
        QuizQuestion dd = new QuizQuestion(subtopic, "Order.", DragDropVariant.SEQUENCING, content, "Exp");
        dd.setId(questionId);

        when(quizQuestionRepository.findBySubtopicId(subtopicId)).thenReturn(List.of(dd));
        when(answerAttemptRepository.findByQuizQuestionId(questionId))
                .thenReturn(List.of(new AnswerAttempt(dd, List.of("B", "A"), false, 0)));
        Snack snack = new Snack(subtopic, "concept", "metaphor", "why", List.of("r1"));
        when(snackRepository.findBySubtopicId(subtopicId)).thenReturn(Optional.of(snack));

        QuizService.QuizSummary summary = quizService.getQuizSummary(subtopicId);

        assertEquals(0, summary.correctAnswers());
        assertEquals(1, summary.questions().size());
        QuizService.QuestionBreakdown bd = summary.questions().get(0);
        assertEquals(DragDropVariant.SEQUENCING, bd.variant());
        assertEquals(List.of("A", "B"), bd.correctArrangement());
        assertEquals(List.of("B", "A"), bd.submittedArrangement());
        assertTrue(summary.recap() != null);
        assertEquals("Light reactions", summary.recap().subtopicName());
        assertEquals(snack, summary.recap().snack());
    }
}
