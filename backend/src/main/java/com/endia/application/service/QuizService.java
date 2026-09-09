package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.enums.DragDropVariant;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.InvalidOptionIndexException;
import com.endia.domain.exception.LlmGenerationException;
import com.endia.domain.exception.QuizAlreadyAnsweredException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.AnswerAttempt;
import com.endia.domain.model.DragDropContent;
import com.endia.domain.model.QuizQuestion;
import com.endia.domain.model.Snack;
import com.endia.domain.model.Subtopic;
import com.endia.infrastructure.persistence.JpaAnswerAttemptRepository;
import com.endia.infrastructure.persistence.JpaQuizQuestionRepository;
import com.endia.infrastructure.persistence.JpaSnackRepository;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);
    private static final int XP_PER_CORRECT = 10;
    private static final int PERFECT_BONUS = 50;
    private static final int MAX_RETRIES = 1;

    private final LlmProvider llmProvider;
    private final QuizQualityGate qualityGate;
    private final JpaSubtopicRepository subtopicRepository;
    private final JpaQuizQuestionRepository quizQuestionRepository;
    private final JpaAnswerAttemptRepository answerAttemptRepository;
    private final JpaSnackRepository snackRepository;
    private final SecureRandom random = new SecureRandom();

    public QuizService(LlmProvider llmProvider,
                       QuizQualityGate qualityGate,
                       JpaSubtopicRepository subtopicRepository,
                       JpaQuizQuestionRepository quizQuestionRepository,
                       JpaAnswerAttemptRepository answerAttemptRepository,
                       JpaSnackRepository snackRepository) {
        this.llmProvider = llmProvider;
        this.qualityGate = qualityGate;
        this.subtopicRepository = subtopicRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.answerAttemptRepository = answerAttemptRepository;
        this.snackRepository = snackRepository;
    }

    @Transactional
    public List<QuizQuestion> generateQuiz(UUID subtopicId, int count) {
        Subtopic subtopic = subtopicRepository.findById(subtopicId)
                .orElseThrow(() -> new EntityNotFoundException("Subtopic", subtopicId));

        String themePrompt = subtopic.getTheme().getPrompt();
        String subtopicName = subtopic.getName();
        DragDropVariant variant = pickVariant();

        List<LlmProvider.GeneratedQuestion> generated;
        try {
            generated = llmProvider.generateQuiz(themePrompt, subtopicName, count, variant);
        } catch (LlmGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException("The AI service is temporarily unavailable. Please try again later.", e);
        }

        List<QuizQuestion> questions = new ArrayList<>();
        for (LlmProvider.GeneratedQuestion q : generated) {
            LlmProvider.GeneratedQuestion processed = ensureValidQuestion(themePrompt, subtopicName, q, count, variant);
            QuizQuestion question = persistQuestion(subtopic, processed);
            quizQuestionRepository.save(question);
            questions.add(question);
        }
        return questions;
    }

    private DragDropVariant pickVariant() {
        DragDropVariant[] values = DragDropVariant.values();
        return values[random.nextInt(values.length)];
    }

    /**
     * Persists a validated question; shuffles MCQ options and keeps drag-drop untouched.
     */
    private QuizQuestion persistQuestion(Subtopic subtopic, LlmProvider.GeneratedQuestion q) {
        if (q.type() == LlmProvider.GeneratedQuestion.QuestionType.DRAG_DROP) {
            return new QuizQuestion(subtopic, q.prompt(), q.variant(), q.dragDropContent(), q.explanation());
        }
        List<String> shuffled = shuffleWithCorrect(q.options(), q.correctIndex() == null ? -1 : q.correctIndex());
        int newCorrectIndex = shuffled.indexOf(q.options().get(q.correctIndex()));
        return new QuizQuestion(subtopic, q.prompt(), shuffled, newCorrectIndex, q.explanation());
    }

    /**
     * Runs the quality gate on a question; re-rolls once if it fails. Serves best result regardless of second failure.
     */
    private LlmProvider.GeneratedQuestion ensureValidQuestion(String theme, String subtopic,
                                                              LlmProvider.GeneratedQuestion q, int count,
                                                              DragDropVariant variant) {
        if (qualityGate.validate(q)) {
            return q;
        }
        log.warn("Quiz question failed quality gate ({}). Re-rolling once.", qualityGate.firstFailureReason(q));
        LlmProvider.GeneratedQuestion reRolled = null;
        try {
            reRolled = llmProvider.generateQuiz(theme, subtopic, count, variant).stream()
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Re-roll generation failed", e);
        }
        if (reRolled != null && qualityGate.validate(reRolled)) {
            return reRolled;
        }
        if (reRolled != null) {
            log.warn("Re-rolled question still failed quality gate ({}). Serving best available result.",
                    qualityGate.firstFailureReason(reRolled));
            return reRolled;
        }
        return q;
    }

    /**
     * Shuffles the options, placing the correct answer at a random position. Returns the shuffled list.
     */
    private List<String> shuffleWithCorrect(List<String> options, int correctIndex) {
        int size = options.size();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, random);

        List<String> shuffled = new ArrayList<>();
        for (int index : indices) {
            shuffled.add(options.get(index));
        }
        return shuffled;
    }

    /**
     * Answers a quiz question (MCQ via chosenIndex or drag-drop via arrangement).
     */
    @Transactional
    public AnswerResult answerQuestion(UUID questionId, int chosenIndex) {
        return answerQuestion(questionId, chosenIndex, null);
    }

    @Transactional
    public AnswerResult answerQuestion(UUID questionId, Integer chosenIndex, List<String> arrangement) {
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question", questionId));

        if (answerAttemptRepository.existsByQuizQuestionId(questionId)) {
            throw new QuizAlreadyAnsweredException(questionId);
        }

        if (question.getType() == com.endia.domain.enums.QuestionType.DRAG_DROP) {
            if (arrangement == null || arrangement.isEmpty()) {
                throw new InvalidOptionIndexException(-1);
            }
            boolean correct = gradeDragDrop(question, arrangement);
            int xpEarned = correct ? XP_PER_CORRECT : 0;
            AnswerAttempt attempt = new AnswerAttempt(question, arrangement, correct, xpEarned);
            answerAttemptRepository.save(attempt);
            return new AnswerResult(questionId, LlmProvider.GeneratedQuestion.QuestionType.DRAG_DROP, correct,
                    null, canonicalArrangement(question), question.getExplanation(), xpEarned);
        }

        if (chosenIndex == null || chosenIndex < 0 || chosenIndex >= question.getOptions().size()) {
            throw new InvalidOptionIndexException(chosenIndex == null ? -1 : chosenIndex);
        }

        boolean correct = chosenIndex == question.getCorrectIndex();
        int xpEarned = correct ? XP_PER_CORRECT : 0;
        AnswerAttempt attempt = new AnswerAttempt(question, chosenIndex, correct, xpEarned);
        answerAttemptRepository.save(attempt);

        return new AnswerResult(questionId, LlmProvider.GeneratedQuestion.QuestionType.MULTIPLE_CHOICE, correct,
                question.getCorrectIndex(), null, question.getExplanation(), xpEarned);
    }

    private boolean gradeDragDrop(QuizQuestion question, List<String> arrangement) {
        DragDropContent content = question.getDragDropContent();
        DragDropVariant variant = content.variant();
        switch (variant) {
            case SEQUENCING:
                return arrangement.equals(content.correctOrder());
            case CLASSIFICATION:
            case MATCHING: {
                Map<String, String> submitted = parsePairs(arrangement);
                Map<String, String> canonical = content.pairs();
                return canonical.equals(submitted)
                        && canonical.keySet().containsAll(content.items());
            }
            default:
                return false;
        }
    }

    private Map<String, String> parsePairs(List<String> entries) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String entry : entries) {
            int idx = entry.indexOf(':');
            if (idx > 0) {
                map.put(entry.substring(0, idx).trim(), entry.substring(idx + 1).trim());
            }
        }
        return map;
    }

    /**
     * Builds the revealed canonical arrangement for a drag-drop question.
     */
    private List<String> canonicalArrangement(QuizQuestion question) {
        DragDropContent content = question.getDragDropContent();
        if (content.variant() == DragDropVariant.SEQUENCING) {
            return content.correctOrder();
        }
        List<String> result = new ArrayList<>();
        content.pairs().forEach((k, v) -> result.add(k + ":" + v));
        return result;
    }

    /**
     * Calculates total XP for a subtopic's quiz: sum of per-answer XP + 50 bonus if all correct.
     */
    public int calculateQuizXp(UUID subtopicId) {
        List<QuizQuestion> questions = quizQuestionRepository.findBySubtopicId(subtopicId);
        int totalBase = 0;
        int answeredCorrectly = 0;
        for (QuizQuestion q : questions) {
            List<AnswerAttempt> attempts = answerAttemptRepository.findByQuizQuestionId(q.getId());
            for (AnswerAttempt a : attempts) {
                totalBase += a.getXpEarned();
                if (a.isCorrect()) {
                    answeredCorrectly++;
                }
            }
        }
        int totalQuestions = questions.size();
        if (totalQuestions > 0 && answeredCorrectly == totalQuestions) {
            return totalBase + PERFECT_BONUS;
        }
        return totalBase;
    }

    /**
     * Aggregates answer attempts for a subtopic into a summary.
     */
    @Transactional(readOnly = true)
    public QuizSummary getQuizSummary(UUID subtopicId) {
        List<QuizQuestion> questions = quizQuestionRepository.findBySubtopicId(subtopicId);
        if (questions.isEmpty()) {
            throw new EntityNotFoundException("No quiz found for subtopic: " + subtopicId);
        }

        List<QuestionBreakdown> breakdown = new ArrayList<>();
        int correctAnswers = 0;
        for (QuizQuestion q : questions) {
            List<AnswerAttempt> attempts = answerAttemptRepository.findByQuizQuestionId(q.getId());
            AnswerAttempt attempt = attempts.isEmpty() ? null : attempts.get(0);
            boolean correct = attempt != null && attempt.isCorrect();
            if (correct) {
                correctAnswers++;
            }
            breakdown.add(buildBreakdown(q, attempt, correct));
        }

        int totalXp = calculateQuizXp(subtopicId);
        Recap recap = buildRecap(subtopicId);
        return new QuizSummary(subtopicId, questions.size(), correctAnswers, totalXp, breakdown, recap);
    }

    private QuestionBreakdown buildBreakdown(QuizQuestion q, AnswerAttempt attempt, boolean correct) {
        if (q.getType() == com.endia.domain.enums.QuestionType.DRAG_DROP) {
            DragDropContent content = q.getDragDropContent();
            List<String> submitted = attempt != null && attempt.getArrangement() != null ? attempt.getArrangement() : List.of();
            return new QuestionBreakdown(q.getId(), LlmProvider.GeneratedQuestion.QuestionType.DRAG_DROP, q.getPrompt(),
                    List.of(), attempt != null ? attempt.getChosenIndex() : null, correct, null,
                    q.getVariant(), canonicalArrangement(q), submitted, q.getExplanation());
        }
        List<String> options = q.getOptions() == null ? List.of() : q.getOptions();
        return new QuestionBreakdown(q.getId(), LlmProvider.GeneratedQuestion.QuestionType.MULTIPLE_CHOICE, q.getPrompt(),
                options, attempt != null ? attempt.getChosenIndex() : null, correct, q.getCorrectIndex(),
                null, List.of(), List.of(), q.getExplanation());
    }

    private Recap buildRecap(UUID subtopicId) {
        return snackRepository.findBySubtopicId(subtopicId)
                .map(snack -> new Recap(subtopicId, snack.getSubtopic().getName(), snack))
                .orElse(null);
    }

    // ---- Result record types ----

    public record AnswerResult(UUID questionId, LlmProvider.GeneratedQuestion.QuestionType type,
                               boolean correct, Integer correctIndex, List<String> correctArrangement,
                               String explanation, int xpEarned) {
    }

    public record QuestionBreakdown(UUID questionId, LlmProvider.GeneratedQuestion.QuestionType type, String prompt,
                                    List<String> options, Integer chosenIndex, boolean correct, Integer correctIndex,
                                    DragDropVariant variant, List<String> correctArrangement,
                                    List<String> submittedArrangement, String explanation) {
    }

    public record QuizSummary(UUID subtopicId, int totalQuestions, int correctAnswers, int totalXp,
                              List<QuestionBreakdown> questions, Recap recap) {
    }

    public record Recap(UUID subtopicId, String subtopicName, Snack snack) {
    }
}
