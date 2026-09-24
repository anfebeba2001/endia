package com.endia.interfaces.rest;

import com.endia.application.service.DefinitionService;
import com.endia.domain.enums.LlmErrorCause;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.InvalidSelectionException;
import com.endia.domain.exception.LlmGenerationException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.infrastructure.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 016 (US1/US2): contract-shape coverage of {@code POST /api/definitions/generate}
 * (FR-004, FR-005, FR-010, FR-011, SC-005). Focused standalone MockMvc mirroring
 * {@code MeaningSnackContractTest}: the controller is wired with a mocked service and the real
 * {@link GlobalExceptionHandler} so the 400/404/503 error shapes are asserted.
 */
@ExtendWith(MockitoExtension.class)
class DefinitionContractTest {

    @Mock
    private DefinitionService definitionService;

    private MockMvc mockMvc;
    private UUID themeId;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new DefinitionController(definitionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
        themeId = UUID.randomUUID();
    }

    private String body(String term) {
        return "{\"themeId\":\"" + themeId + "\",\"term\":\"" + term + "\"}";
    }

    private static String tokens(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
    }

    @Test
    void generateReturnsTheTwoFieldDefinitionShape() throws Exception {
        when(definitionService.generate(any(), anyString()))
                .thenReturn(new DefinitionService.DefinitionResult(
                        "photosynthesis", "the process plants use to convert light into energy"));

        mockMvc.perform(post("/api/definitions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("photosynthesis")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.term").value("photosynthesis"))
                .andExpect(jsonPath("$.definition")
                        .value("the process plants use to convert light into energy"))
                // Exactly two fields, no id/persisted/quiz/keywords fields.
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.kind").doesNotExist())
                .andExpect(jsonPath("$.keywords").doesNotExist())
                .andExpect(jsonPath("$.quizQuestions").doesNotExist());
    }

    @Test
    void blankTermIsAValidationError() throws Exception {
        mockMvc.perform(post("/api/definitions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("term"));
    }

    @Test
    void missingThemeIdIsAValidationError() throws Exception {
        mockMvc.perform(post("/api/definitions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"term\":\"photosynthesis\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("themeId"));
    }

    @Test
    void overTenTokenTermIsAValidationError() throws Exception {
        when(definitionService.generate(any(), anyString()))
                .thenThrow(new InvalidSelectionException("Select no more than 10 words to define."));

        mockMvc.perform(post("/api/definitions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tokens(11))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Select no more than 10 words to define."));
    }

    @Test
    void unknownThemeIsNotFound() throws Exception {
        when(definitionService.generate(any(), anyString()))
                .thenThrow(new EntityNotFoundException("Theme", themeId));

        mockMvc.perform(post("/api/definitions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("photosynthesis")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void llmFailureIsServiceUnavailableWithCause() throws Exception {
        when(definitionService.generate(any(), anyString()))
                .thenThrow(new LlmGenerationException(LlmErrorCause.UNAVAILABLE, true,
                        "The AI service is temporarily unavailable. Please try again later."));

        mockMvc.perform(post("/api/definitions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("photosynthesis")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.cause").value("UNAVAILABLE"));
    }

    @Test
    void validationFailureAfterReRollIsServiceUnavailable() throws Exception {
        when(definitionService.generate(any(), anyString()))
                .thenThrow(new ServiceUnavailableException(
                        "The AI service returned an incomplete definition. Please try again."));

        mockMvc.perform(post("/api/definitions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("photosynthesis")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }
}
