package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.InvalidOptionIndexException;
import com.endia.domain.exception.QuizAlreadyAnsweredException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.AnswerAttempt;
import com.endia.domain.model.QuizQuestion;
import com.endia.domain.model.Subtopic;
import com.endia.infrastructure.persistence.JpaAnswerAttemptRepository;
import com.endia.infrastructure.persistence.JpaQuizQuestionRepository;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final SecureRandom random = new SecureRandom();

    public QuizService(LlmProvider llmProvider,
                       QuizQualityGate qualityGate,
                       JpaSubtopicRepository subtopicRepository,
                       JpaQuizQuestionRepository quizQuestionRepository,
                       JpaAnswerAttemptRepository answerAttemptRepository) {
        this.llmProvider = llmProvider;
        this.qualityGate = qualityGate;
        this.subtopicRepository = subtopicRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.answerAttemptRepository = answerAttemptRepository;
    }

    @Transactional
    public List<QuizQuestion> generateQuiz(UUID subtopicId, int count) {
        Subtopic subtopic = subtopicRepository.findById(subtopicId)
                .orElseThrow(() -> new EntityNotFoundException("Subtopic", subtopicId));

        String themePrompt = subtopic.getTheme().getPrompt();
        String subtopicName = subtopic.getName();

        List<LlmProvider.GeneratedQuestion> generated;
        try {
            generated = llmProvider.generateQuiz(themePrompt, subtopicName, count);
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException("The AI service is temporarily unavailable. Please try again later.", e);
        }

        List<QuizQuestion> questions = new ArrayList<>();
        for (LlmProvider.GeneratedQuestion q : generated) {
            LlmProvider.GeneratedQuestion processed = ensureValidQuestion(themePrompt, subtopicName, q, count);
            // Shuffle correct answer to random position
            List<String> shuffled = shuffleWithCorrect(processed.options(), processed.correctIndex());
            int newCorrectIndex = shuffled.indexOf(processed.options().get(processed.correctIndex()));

            QuizQuestion question = new QuizQuestion(subtopic, processed.prompt(), shuffled, newCorrectIndex, processed.explanation());
            quizQuestionRepository.save(question);
            questions.add(question);
        }
        return questions;
    }

    /**
     * Runs the quality gate on a question; re-rolls once if it fails. Serves best result regardless of second failure.
     */
    private LlmProvider.GeneratedQuestion ensureValidQuestion(String theme, String subtopic, LlmProvider.GeneratedQuestion q, int count) {
        if (qualityGate.validate(q)) {
            return q;
        }
        log.warn("Quiz question failed quality gate ({}). Re-rolling once.", qualityGate.firstFailureReason(q));
        LlmProvider.GeneratedQuestion reRolled = null;
        try {
            reRolled = llmProvider.generateQuiz(theme, subtopic, count).stream()
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
        // Fall back to original
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

        // Ensure the correct option is at a random position (shuffle naturally does this).
        List<String> shuffled = new ArrayList<>();
        for (int index : indices) {
            shuffled.add(options.get(index));
        }
        return shuffled;
    }

    /**
     * Answers a quiz question. Validates question exists, not already answered, and chosenIndex range.
     */
    @Transactional
    public AnswerResult answerQuestion(UUID questionId, int chosenIndex) {
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question", questionId));

        if (answerAttemptRepository.existsByQuizQuestionId(questionId)) {
            throw new QuizAlreadyAnsweredException(questionId);
        }
        if (chosenIndex < 0 || chosenIndex >= question.getOptions().size()) {
            throw new InvalidOptionIndexException(chosenIndex);
        }

        boolean correct = chosenIndex == question.getCorrectIndex();
        int xpEarned = correct ? XP_PER_CORRECT : 0;

        AnswerAttempt attempt = new AnswerAttempt(question, chosenIndex, correct, xpEarned);
        answerAttemptRepository.save(attempt);

        return new AnswerResult(questionId, correct, question.getCorrectIndex(), question.getExplanation(), xpEarned);
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
            breakdown.add(new QuestionBreakdown(
                    q.getId(),
                    q.getPrompt(),
                    q.getOptions(),
                    attempt != null ? attempt.getChosenIndex() : -1,
                    correct,
                    q.getCorrectIndex(),
                    q.getExplanation()
            ));
        }

        int totalXp = calculateQuizXp(subtopicId);
        return new QuizSummary(subtopicId, questions.size(), correctAnswers, totalXp, breakdown);
    }

    // ---- Result record types ----

    public record AnswerResult(UUID questionId, boolean correct, int correctIndex, String explanation, int xpEarned) {
    }

    public record QuestionBreakdown(UUID questionId, String prompt, List<String> options,
                                    int chosenIndex, boolean correct, int correctIndex, String explanation) {
    }

    public record QuizSummary(UUID subtopicId, int totalQuestions, int correctAnswers, int totalXp,
                              List<QuestionBreakdown> questions) {
    }
}
