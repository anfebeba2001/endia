package com.endia.interfaces.rest;

import com.endia.application.service.QuizService;
import com.endia.domain.model.QuizQuestion;
import com.endia.domain.model.Snack;
import com.endia.interfaces.rest.dto.QuizAnswerApiRequest;
import com.endia.interfaces.rest.dto.QuizAnswerApiResponse;
import com.endia.interfaces.rest.dto.QuizGenerateApiRequest;
import com.endia.interfaces.rest.dto.QuizGenerateApiResponse;
import com.endia.interfaces.rest.dto.QuizSummaryApiResponse;
import com.endia.interfaces.rest.dto.SnackKeywordDto;
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
     * Generates a quiz for a subtopic. correctIndex, explanation and the correct
     * arrangement are stripped from the response (MCQ + drag-drop).
     */
    @PostMapping("/generate")
    public ResponseEntity<QuizGenerateApiResponse> generate(@Valid @RequestBody QuizGenerateApiRequest request) {
        List<QuizQuestion> questions = quizService.generateQuiz(request.subtopicId(), request.countOrDefault());

        List<QuizGenerateApiResponse.QuestionItem> items = questions.stream()
                .map(QuizGenerateApiResponse::from)
                .toList();

        return ResponseEntity.ok(new QuizGenerateApiResponse(request.subtopicId(), items));
    }

    /**
     * Answers a quiz question and returns correct status, correct answer, explanation, and XP.
     */
    @PostMapping("/answer")
    public ResponseEntity<QuizAnswerApiResponse> answer(@Valid @RequestBody QuizAnswerApiRequest request) {
        QuizService.AnswerResult result = quizService.answerQuestion(request.questionId(), request.chosenIndex(), request.arrangement());

        return ResponseEntity.ok(new QuizAnswerApiResponse(
                result.questionId(),
                result.type().name(),
                result.correct(),
                result.correctIndex(),
                result.correctArrangement(),
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
                        q.type().name(),
                        q.prompt(),
                        q.options(),
                        q.chosenIndex(),
                        q.correct(),
                        q.correctIndex(),
                        q.variant() == null ? null : q.variant().name(),
                        q.correctArrangement(),
                        q.submittedArrangement(),
                        q.explanation()))
                .toList();

        QuizSummaryApiResponse.Recap recap = null;
        if (summary.recap() != null) {
            QuizService.Recap r = summary.recap();
            recap = new QuizSummaryApiResponse.Recap(r.subtopicId(), r.subtopicName(), toSnackDto(r.snack()));
        }

        return ResponseEntity.ok(new QuizSummaryApiResponse(
                summary.subtopicId(),
                summary.totalQuestions(),
                summary.correctAnswers(),
                summary.totalXp(),
                breakdown,
                recap
        ));
    }

    private QuizSummaryApiResponse.SnackDto toSnackDto(Snack s) {
        if (s == null) {
            return null;
        }
        return new QuizSummaryApiResponse.SnackDto(
                s.getId(),
                s.getSubtopic().getId(),
                s.getSubtopic().getName(),
                s.getConcept(),
                s.getMetaphor(),
                s.getWhyItMatters(),
                s.getRelated(),
                s.getKeywords() == null
                        ? List.of()
                        : s.getKeywords().stream().map(SnackKeywordDto::from).toList());
    }
}
