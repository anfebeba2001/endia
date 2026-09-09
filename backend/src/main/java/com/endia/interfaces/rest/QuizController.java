package com.endia.interfaces.rest;

import com.endia.application.service.QuizService;
import com.endia.domain.model.QuizQuestion;
import com.endia.interfaces.rest.dto.QuizAnswerApiRequest;
import com.endia.interfaces.rest.dto.QuizAnswerApiResponse;
import com.endia.interfaces.rest.dto.QuizGenerateApiRequest;
import com.endia.interfaces.rest.dto.QuizGenerateApiResponse;
import com.endia.interfaces.rest.dto.QuizSummaryApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    /**
     * Generates a quiz for a subtopic. correctIndex and explanation are stripped from the response.
     */
    @PostMapping("/generate")
    public ResponseEntity<QuizGenerateApiResponse> generate(@Valid @RequestBody QuizGenerateApiRequest request) {
        List<QuizQuestion> questions = quizService.generateQuiz(request.subtopicId(), request.countOrDefault());

        List<QuizGenerateApiResponse.QuestionItem> items = questions.stream()
                .map(q -> new QuizGenerateApiResponse.QuestionItem(q.getId(), q.getPrompt(), q.getOptions()))
                .toList();

        return ResponseEntity.ok(new QuizGenerateApiResponse(request.subtopicId(), items));
    }

    /**
     * Answers a quiz question and returns correct status, correctIndex, explanation, and XP.
     */
    @PostMapping("/answer")
    public ResponseEntity<QuizAnswerApiResponse> answer(@Valid @RequestBody QuizAnswerApiRequest request) {
        QuizService.AnswerResult result = quizService.answerQuestion(request.questionId(), request.chosenIndex());

        return ResponseEntity.ok(new QuizAnswerApiResponse(
                result.questionId(),
                result.correct(),
                result.correctIndex(),
                result.explanation(),
                result.xpEarned()
        ));
    }

    /**
     * Returns a quiz summary for a subtopic after all questions have been answered.
     */
    @GetMapping("/summary/{subtopicId}")
    public ResponseEntity<QuizSummaryApiResponse> summary(@PathVariable UUID subtopicId) {
        QuizService.QuizSummary summary = quizService.getQuizSummary(subtopicId);

        List<QuizSummaryApiResponse.QuestionBreakdown> breakdown = summary.questions().stream()
                .map(q -> new QuizSummaryApiResponse.QuestionBreakdown(
                        q.questionId(),
                        q.prompt(),
                        q.options(),
                        q.chosenIndex(),
                        q.correct(),
                        q.correctIndex(),
                        q.explanation()))
                .toList();

        return ResponseEntity.ok(new QuizSummaryApiResponse(
                summary.subtopicId(),
                summary.totalQuestions(),
                summary.correctAnswers(),
                summary.totalXp(),
                breakdown
        ));
    }
}
