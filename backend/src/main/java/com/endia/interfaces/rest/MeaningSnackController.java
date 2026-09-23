package com.endia.interfaces.rest;

import com.endia.application.service.MeaningSnackService;
import com.endia.domain.model.MeaningSnack;
import com.endia.interfaces.rest.dto.MeaningSnackGenerateApiRequest;
import com.endia.interfaces.rest.dto.MeaningSnackGenerateApiResponse;
import com.endia.interfaces.rest.dto.MeaningSnackItem;
import com.endia.interfaces.rest.dto.MeaningSnackListApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Meaning-snack endpoint group (feature 014, FR-001/FR-004/FR-005/FR-006/FR-014/FR-015/FR-016/
 * FR-018/FR-020). Errors use the existing {@code GlobalExceptionHandler} shapes (400/404/503).
 */
@RestController
@RequestMapping("/api/meaning-snacks")
public class MeaningSnackController {

    private final MeaningSnackService meaningSnackService;

    public MeaningSnackController(MeaningSnackService meaningSnackService) {
        this.meaningSnackService = meaningSnackService;
    }

    @PostMapping("/generate")
    public ResponseEntity<MeaningSnackGenerateApiResponse> generate(
            @Valid @RequestBody MeaningSnackGenerateApiRequest request) {
        MeaningSnackService.GenerateResult result = meaningSnackService.generate(
                request.themeId(), request.subtopicId(), request.term(), request.definition());
        MeaningSnack snack = result.snack();
        return ResponseEntity.ok(new MeaningSnackGenerateApiResponse(
                result.alreadyExisted(), toItem(snack)));
    }

    @GetMapping
    public ResponseEntity<MeaningSnackListApiResponse> list(@RequestParam UUID themeId) {
        List<MeaningSnackItem> items = meaningSnackService.listByTheme(themeId).stream()
                .map(MeaningSnackController::toItem)
                .toList();
        return ResponseEntity.ok(new MeaningSnackListApiResponse(themeId, items));
    }

    private static MeaningSnackItem toItem(MeaningSnack snack) {
        String subtopicName = snack.getSourceSubtopic() != null ? snack.getSourceSubtopic().getName() : null;
        return MeaningSnackItem.from(snack, subtopicName);
    }
}
