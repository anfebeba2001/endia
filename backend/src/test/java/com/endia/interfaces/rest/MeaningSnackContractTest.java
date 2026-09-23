package com.endia.interfaces.rest;

import com.endia.application.service.MeaningSnackService;
import com.endia.domain.enums.LlmErrorCause;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.LlmGenerationException;
import com.endia.domain.model.MeaningSnack;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 014 (US1 / M3): contract-shape coverage of the {@code /api/meaning-snacks} endpoint group
 * (FR-001, FR-004..FR-006, FR-008, FR-016, SC-001, SC-003, SC-005). Uses a focused standalone MockMvc
 * mirroring {@code SnackMarkupRoundTripTest}: the controller is wired with a mocked service and the
 * real {@link GlobalExceptionHandler} so the 400/404/503 error shapes are asserted.
 */
@ExtendWith(MockitoExtension.class)
class MeaningSnackContractTest {

    @Mock
    private MeaningSnackService meaningSnackService;

    private MockMvc mockMvc;
    private UUID themeId;
    private UUID subtopicId;
    private MeaningSnack snack;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new MeaningSnackController(meaningSnackService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();

        themeId = UUID.randomUUID();
        subtopicId = UUID.randomUUID();
        Theme theme = new Theme("Photosynthesis");
        theme.setId(themeId);
        Subtopic subtopic = new Subtopic(theme, "Light reactions");
        subtopic.setId(subtopicId);
        snack = new MeaningSnack(theme, subtopic, "photosynthesis", "light to sugar",
                "**Photosynthesis** converts light.", "A **solar kitchen**.", "Food chains start here.",
                List.of("Chlorophyll", "Glucose"));
        snack.setId(UUID.randomUUID());
        snack.setCreatedAt(LocalDateTime.of(2026, 9, 23, 12, 0));
    }

    private String generateBody(String term, String definition) {
        return "{\"themeId\":\"" + themeId + "\",\"subtopicId\":\"" + subtopicId + "\",\"term\":\""
                + term + "\",\"definition\":\"" + definition + "\"}";
    }

    @Test
    void generateReturnsTheTwelveFieldNoQuizShape() throws Exception {
        when(meaningSnackService.generate(any(), any(), anyString(), anyString()))
                .thenReturn(new MeaningSnackService.GenerateResult(snack, false));

        mockMvc.perform(post("/api/meaning-snacks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody("photosynthesis", "light to sugar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyExisted").value(false))
                .andExpect(jsonPath("$.meaningSnack.kind").value("MEANING"))
                .andExpect(jsonPath("$.meaningSnack.themeId").value(themeId.toString()))
                .andExpect(jsonPath("$.meaningSnack.sourceSubtopicId").value(subtopicId.toString()))
                .andExpect(jsonPath("$.meaningSnack.sourceSubtopicName").value("Light reactions"))
                .andExpect(jsonPath("$.meaningSnack.sourceTerm").value("photosynthesis"))
                .andExpect(jsonPath("$.meaningSnack.sourceDefinition").value("light to sugar"))
                .andExpect(jsonPath("$.meaningSnack.concept").value("**Photosynthesis** converts light."))
                .andExpect(jsonPath("$.meaningSnack.metaphor").value("A **solar kitchen**."))
                .andExpect(jsonPath("$.meaningSnack.whyItMatters").value("Food chains start here."))
                .andExpect(jsonPath("$.meaningSnack.related[0]").value("Chlorophyll"))
                .andExpect(jsonPath("$.meaningSnack.createdAt").exists())
                // Exactly the 12 MeaningSnackItem fields.
                .andExpect(jsonPath("$.meaningSnack.length()").value(12))
                // No quiz fields and no keywords.
                .andExpect(jsonPath("$.meaningSnack.subtopicId").doesNotExist())
                .andExpect(jsonPath("$.meaningSnack.quizStatus").doesNotExist())
                .andExpect(jsonPath("$.meaningSnack.quizQuestions").doesNotExist())
                .andExpect(jsonPath("$.meaningSnack.keywords").doesNotExist());
    }

    @Test
    void generateReturnsAlreadyExistedTrueWhenPresent() throws Exception {
        when(meaningSnackService.generate(any(), any(), anyString(), anyString()))
                .thenReturn(new MeaningSnackService.GenerateResult(snack, true));

        mockMvc.perform(post("/api/meaning-snacks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody("photosynthesis", "light to sugar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyExisted").value(true));
    }

    @Test
    void listReturnsThemeIdAndMeaningSnacks() throws Exception {
        when(meaningSnackService.listByTheme(themeId)).thenReturn(List.of(snack));

        mockMvc.perform(get("/api/meaning-snacks").param("themeId", themeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themeId").value(themeId.toString()))
                .andExpect(jsonPath("$.meaningSnacks[0].kind").value("MEANING"))
                .andExpect(jsonPath("$.meaningSnacks[0].sourceTerm").value("photosynthesis"))
                .andExpect(jsonPath("$.meaningSnacks[0].length()").value(12))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listReturnsEmptyArrayWhenThemeHasNone() throws Exception {
        when(meaningSnackService.listByTheme(themeId)).thenReturn(List.of());

        mockMvc.perform(get("/api/meaning-snacks").param("themeId", themeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themeId").value(themeId.toString()))
                .andExpect(jsonPath("$.meaningSnacks").isArray())
                .andExpect(jsonPath("$.meaningSnacks.length()").value(0));
    }

    @Test
    void missingThemeIdIsAValidationErrorNotAnInternalError() throws Exception {
        // Contract §2.3: a missing themeId is 400 VALIDATION_ERROR, not a 500 fall-through.
        mockMvc.perform(get("/api/meaning-snacks"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: themeId"))
                .andExpect(jsonPath("$.field").value("themeId"))
                .andExpect(jsonPath("$.cause").doesNotExist());
    }

    @Test
    void nonUuidThemeIdIsAValidationError() throws Exception {
        // Regression guard: the existing type-mismatch mapping still yields the same 400 shape.
        mockMvc.perform(get("/api/meaning-snacks").param("themeId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("themeId"));
    }

    @Test
    void blankTermIsAValidationError() throws Exception {
        mockMvc.perform(post("/api/meaning-snacks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody("   ", "light to sugar")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("term"));
    }

    @Test
    void unknownThemeIsNotFound() throws Exception {
        when(meaningSnackService.generate(any(), any(), anyString(), anyString()))
                .thenThrow(new EntityNotFoundException("Theme", themeId));

        mockMvc.perform(post("/api/meaning-snacks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody("photosynthesis", "light to sugar")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void llmFailureIsServiceUnavailableWithCause() throws Exception {
        when(meaningSnackService.generate(any(), any(), anyString(), anyString()))
                .thenThrow(new LlmGenerationException(LlmErrorCause.UNAVAILABLE, true,
                        "The AI service is temporarily unavailable. Please try again later."));

        // The existing GlobalExceptionHandler maps a typed LlmGenerationException to HTTP 503 with
        // the machine-readable cause in both `error` and `cause` (consistent with regular snacks).
        mockMvc.perform(post("/api/meaning-snacks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody("photosynthesis", "light to sugar")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.cause").value("UNAVAILABLE"));
    }

    @Test
    void validationFailureAfterReRollIsServiceUnavailable() throws Exception {
        when(meaningSnackService.generate(any(), any(), anyString(), anyString()))
                .thenThrow(new com.endia.domain.exception.ServiceUnavailableException(
                        "The AI service returned an incomplete meaning snack. Please try again."));

        mockMvc.perform(post("/api/meaning-snacks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody("photosynthesis", "light to sugar")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }
}
