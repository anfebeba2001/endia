package com.endia.interfaces.rest;

import com.endia.application.service.BatchService;
import com.endia.domain.enums.GenerationStatus;
import com.endia.domain.model.QuizQuestion;
import com.endia.domain.model.Snack;
import com.endia.interfaces.rest.dto.BatchCreateApiRequest;
import com.endia.interfaces.rest.dto.BatchCreateApiResponse;
import com.endia.interfaces.rest.dto.BatchStatusApiResponse;
import com.endia.interfaces.rest.dto.QuizGenerateApiResponse;
import com.endia.interfaces.rest.dto.SnackKeywordDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    /**
     * One batch trigger for snacks + quiz prefetch. Returns 202 immediately (FR-001).
     */
    @PostMapping
    public ResponseEntity<BatchCreateApiResponse> create(@Valid @RequestBody BatchCreateApiRequest request) {
        BatchService.BatchCreateResult result = batchService.createBatch(request.themeId(), request.subtopicIds());

        List<BatchCreateApiResponse.BatchItem> items = result.items().stream()
                .map(i -> new BatchCreateApiResponse.BatchItem(
                        i.subtopicId(), i.snackStatus().name(), i.quizStatus().name()))
                .toList();

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new BatchCreateApiResponse(result.batchId(), result.themeId(), result.status().name(), items));
    }

    /**
     * Poll batch status (FR-002/003/004, FR-017 + cause).
     */
    @GetMapping("/{batchId}")
    public ResponseEntity<BatchStatusApiResponse> status(@PathVariable UUID batchId) {
        BatchService.BatchStatusResult result = batchService.getStatus(batchId);

        List<BatchStatusApiResponse.BatchItem> items = result.items().stream()
                .map(i -> new BatchStatusApiResponse.BatchItem(
                        i.subtopicId(),
                        i.subtopicName(),
                        i.snackStatus(),
                        i.snackPhase() == null ? null : i.snackPhase().name(),
                        toSnackDto(i.snack()),
                        i.snackCause(),
                        i.quizStatus(),
                        i.quizCause(),
                        toQuizItemDtos(i.quizQuestions())))
                .toList();

        return ResponseEntity.ok(new BatchStatusApiResponse(
                result.batchId(), result.themeId(), result.status().name(), result.retrying(), items));
    }

    private List<QuizGenerateApiResponse.QuestionItem> toQuizItemDtos(List<QuizQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            return null;
        }
        return questions.stream().map(QuizGenerateApiResponse::from).toList();
    }

    private BatchStatusApiResponse.SnackDto toSnackDto(Snack s) {
        if (s == null) {
            return null;
        }
        return new BatchStatusApiResponse.SnackDto(
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
