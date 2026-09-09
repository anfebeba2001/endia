package com.endia.interfaces.rest;

import com.endia.application.service.SnackService;
import com.endia.domain.model.Snack;
import com.endia.interfaces.rest.dto.SnackGenerateApiRequest;
import com.endia.interfaces.rest.dto.SnackGenerateApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/snacks")
public class SnackController {

    private final SnackService snackService;

    public SnackController(SnackService snackService) {
        this.snackService = snackService;
    }

    @PostMapping("/generate")
    public ResponseEntity<SnackGenerateApiResponse> generate(@Valid @RequestBody SnackGenerateApiRequest request) {
        List<Snack> snacks = snackService.generateSnacks(request.themeId(), request.subtopicIds());

        List<SnackGenerateApiResponse.SnackItem> items = snacks.stream()
                .map(s -> new SnackGenerateApiResponse.SnackItem(
                        s.getId(),
                        s.getSubtopic().getId(),
                        s.getSubtopic().getName(),
                        s.getConcept(),
                        s.getMetaphor(),
                        s.getWhyItMatters(),
                        s.getRelated()))
                .toList();

        return ResponseEntity.ok(new SnackGenerateApiResponse(items));
    }
}
