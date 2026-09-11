package com.endia.interfaces.rest;

import com.endia.application.service.BatchService;
import com.endia.domain.enums.BatchStatus;
import com.endia.domain.enums.GenerationStatus;
import com.endia.domain.enums.SnackGenerationPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for feature 008: {@code GET /api/batches/{batchId}} adds a nullable
 * {@code snackPhase} per item, non-null only while {@code snackStatus == GENERATING},
 * with no other shape change. Uses a focused MockMvc setup (no DB).
 */
@ExtendWith(MockitoExtension.class)
class BatchStatusContractTest {

    @Mock
    private BatchService batchService;

    private MockMvc mockMvc;
    private UUID batchId;
    private UUID subtopicId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BatchController(batchService)).build();
        batchId = UUID.randomUUID();
        subtopicId = UUID.randomUUID();
    }

    private BatchService.BatchStatusResult resultWith(SnackGenerationPhase phase, GenerationStatus snackStatus) {
        BatchService.BatchItemStatus item = new BatchService.BatchItemStatus(
                subtopicId, "Light reactions", snackStatus, phase, null, null,
                GenerationStatus.READY, null, List.of());
        return new BatchService.BatchStatusResult(
                batchId, UUID.randomUUID(), BatchStatus.IN_PROGRESS, true, List.of(item));
    }

    @Test
    void generatingItemIncludesSnackPhase() throws Exception {
        when(batchService.getStatus(batchId))
                .thenReturn(resultWith(SnackGenerationPhase.AWAITING_MODEL, GenerationStatus.GENERATING));

        mockMvc.perform(get("/api/batches/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].subtopicId").value(subtopicId.toString()))
                .andExpect(jsonPath("$.items[0].subtopicName").value("Light reactions"))
                .andExpect(jsonPath("$.items[0].snackStatus").value("GENERATING"))
                .andExpect(jsonPath("$.items[0].snackPhase").value("AWAITING_MODEL"))
                .andExpect(jsonPath("$.items[0].snack").value(nullValue()))
                .andExpect(jsonPath("$.items[0].snackCause").value(nullValue()))
                .andExpect(jsonPath("$.items[0].quizStatus").value("READY"))
                .andExpect(jsonPath("$.items[0].quizCause").value(nullValue()))
                .andExpect(jsonPath("$.batchId").value(batchId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.retrying").value(true));
    }

    @Test
    void settledItemHasNullSnackPhase() throws Exception {
        when(batchService.getStatus(batchId))
                .thenReturn(resultWith(null, GenerationStatus.READY));

        mockMvc.perform(get("/api/batches/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].snackStatus").value("READY"))
                .andExpect(jsonPath("$.items[0].snackPhase").value(nullValue()));
    }

    @Test
    void failedItemHasNullSnackPhase() throws Exception {
        when(batchService.getStatus(batchId))
                .thenReturn(resultWith(null, GenerationStatus.FAILED));

        mockMvc.perform(get("/api/batches/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].snackStatus").value("FAILED"))
                .andExpect(jsonPath("$.items[0].snackPhase").value(nullValue()));
    }
}
