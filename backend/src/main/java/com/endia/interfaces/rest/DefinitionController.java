package com.endia.interfaces.rest;

import com.endia.application.service.DefinitionService;
import com.endia.interfaces.rest.dto.DefinitionGenerateApiRequest;
import com.endia.interfaces.rest.dto.DefinitionGenerateApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Definition endpoint group (feature 016, FR-004/FR-005/FR-011). Errors use the existing
 * {@code GlobalExceptionHandler} shapes (400 {@code VALIDATION_ERROR}, 404 {@code NOT_FOUND},
 * 503 {@code SERVICE_UNAVAILABLE}).
 */
@RestController
@RequestMapping("/api/definitions")
public class DefinitionController {

    private final DefinitionService definitionService;

    public DefinitionController(DefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @PostMapping("/generate")
    public ResponseEntity<DefinitionGenerateApiResponse> generate(
            @Valid @RequestBody DefinitionGenerateApiRequest request) {
        DefinitionService.DefinitionResult result =
                definitionService.generate(request.themeId(), request.term());
        return ResponseEntity.ok(new DefinitionGenerateApiResponse(result.term(), result.definition()));
    }
}
