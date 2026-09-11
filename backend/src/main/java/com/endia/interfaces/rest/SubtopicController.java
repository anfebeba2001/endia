package com.endia.interfaces.rest;

import com.endia.application.service.SubtopicService;
import com.endia.domain.model.Subtopic;
import com.endia.interfaces.rest.dto.SubtopicAddApiRequest;
import com.endia.interfaces.rest.dto.SubtopicAddApiResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/themes")
public class SubtopicController {

    private static final Logger log = LoggerFactory.getLogger(SubtopicController.class);

    private final SubtopicService subtopicService;

    public SubtopicController(SubtopicService subtopicService) {
        this.subtopicService = subtopicService;
    }

    /**
     * Add user-proposed subtopics to a theme (FR-019..021).
     */
    @PostMapping("/{themeId}/subtopics")
    public ResponseEntity<SubtopicAddApiResponse> add(@PathVariable UUID themeId,
                                                      @Valid @RequestBody SubtopicAddApiRequest request) {
        log.info("Adding {} custom subtopic(s) to theme {}", request.names().size(), themeId);
        try {
            SubtopicService.AddResult result = subtopicService.addSubtopics(themeId, request.names());

            List<SubtopicAddApiResponse.AddedSubtopic> added = result.added().stream()
                    .map(s -> new SubtopicAddApiResponse.AddedSubtopic(
                            s.getId(), s.getName(), s.getSource().name()))
                    .toList();

            log.info("Added {} custom subtopic(s) to theme {} ({} duplicate(s))",
                    added.size(), themeId, result.duplicates().size());
            return ResponseEntity.ok(new SubtopicAddApiResponse(result.themeId(), added, result.duplicates()));
        } catch (RuntimeException e) {
            // Surface the real cause in the logs instead of the silent generic INTERNAL_ERROR.
            log.error("Failed to add custom subtopic(s) to theme {}", themeId, e);
            throw e;
        }
    }
}
