package com.endia.interfaces.rest;

import com.endia.application.service.BatchService;
import com.endia.application.service.QuizService;
import com.endia.application.service.SnackService;
import com.endia.application.service.ThemeService;
import com.endia.domain.enums.GenerationStatus;
import com.endia.domain.model.Snack;
import com.endia.domain.model.SnackKeyword;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 012 (US4): markup-bearing snack text survives every snack-returning API surface
 * unmodified — not stripped, collapsed, or HTML-escaped — and the JSON shape is unchanged
 * (FR-007, FR-008, FR-009, SC-006, SC-007).
 *
 * <p>Uses a focused MockMvc setup (no DB) mirroring {@code ThemeHistoryContractTest}: the four
 * snack-returning controllers are wired with mocked services that hand back markup-bearing values.
 * The contract-shape assertion counts the object keys ({@code length()}) so no new field can slip
 * in unnoticed.
 */
@ExtendWith(MockitoExtension.class)
class SnackMarkupRoundTripTest {

    /** Bold + bullet + blank-line paragraphs + an injection payload, all in one field. */
    private static final String CONCEPT =
            "**Bold** concept.\n\nTwo stages:\n- bullet one\n- bullet two\n\n"
                    + "<script>window.__xss=1</script> and <img src=x onerror=alert(1)>";
    private static final String METAPHOR = "A **solar kitchen**.";
    private static final String WHY = "Why it matters with <script>alert(1)</script> payload.";
    private static final List<String> RELATED = List.of("Alpha", "Beta");
    /** Keyword text also carries markup to prove it is not stripped or HTML-escaped (FR-011). */
    private static final List<SnackKeyword> KEYWORDS = List.of(
            new SnackKeyword("mitosis", "cell division"),
            new SnackKeyword("<b>ATP</b>", "energy <currency>"));

    @Mock
    private SnackService snackService;
    @Mock
    private BatchService batchService;
    @Mock
    private QuizService quizService;
    @Mock
    private ThemeService themeService;

    private MockMvc mockMvc;
    private UUID themeId;
    private UUID subtopicId;
    private Snack snack;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SnackController(snackService),
                        new BatchController(batchService),
                        new QuizController(quizService),
                        new ThemeController(themeService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();

        themeId = UUID.randomUUID();
        subtopicId = UUID.randomUUID();

        Theme theme = new Theme("Photosynthesis");
        theme.setId(themeId);
        Subtopic subtopic = new Subtopic(theme, "Light reactions");
        subtopic.setId(subtopicId);
        snack = new Snack(subtopic, CONCEPT, METAPHOR, WHY, RELATED, KEYWORDS);
        snack.setId(UUID.randomUUID());
    }

    @Test
    void freshGenerationResponseReturnsMarkupUnmodifiedWithUnchangedShape() throws Exception {
        when(snackService.generateSnacks(any(), anyList())).thenReturn(List.of(snack));

        String body = "{\"themeId\":\"" + themeId + "\",\"subtopicIds\":[\"" + subtopicId + "\"]}";
        mockMvc.perform(post("/api/snacks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snacks[0].concept").value(CONCEPT))
                .andExpect(jsonPath("$.snacks[0].metaphor").value(METAPHOR))
                .andExpect(jsonPath("$.snacks[0].whyItMatters").value(WHY))
                .andExpect(jsonPath("$.snacks[0].related[0]").value("Alpha"))
                // Feature 013: keywords are present, in order, and neither stripped nor HTML-escaped.
                .andExpect(jsonPath("$.snacks[0].keywords[0].term").value("mitosis"))
                .andExpect(jsonPath("$.snacks[0].keywords[0].definition").value("cell division"))
                .andExpect(jsonPath("$.snacks[0].keywords[1].term").value("<b>ATP</b>"))
                .andExpect(jsonPath("$.snacks[0].keywords[1].definition").value("energy <currency>"))
                // Contract shape: exactly the 8 SnackItem fields (7 existing + keywords).
                .andExpect(jsonPath("$.snacks[0].length()").value(8));
    }

    @Test
    void batchStatusResponseReturnsMarkupUnmodifiedWithUnchangedShape() throws Exception {
        BatchService.BatchItemStatus item = new BatchService.BatchItemStatus(
                subtopicId, "Light reactions", GenerationStatus.READY, null, snack,
                null, GenerationStatus.READY, null, List.of());
        when(batchService.getStatus(any())).thenReturn(new BatchService.BatchStatusResult(
                UUID.randomUUID(), themeId, com.endia.domain.enums.BatchStatus.COMPLETE, false, List.of(item)));

        mockMvc.perform(get("/api/batches/{batchId}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].snack.concept").value(CONCEPT))
                .andExpect(jsonPath("$.items[0].snack.whyItMatters").value(WHY))
                .andExpect(jsonPath("$.items[0].snack.keywords[0].term").value("mitosis"))
                .andExpect(jsonPath("$.items[0].snack.keywords[1].definition").value("energy <currency>"))
                .andExpect(jsonPath("$.items[0].snack.length()").value(8));
    }

    @Test
    void resultsRecapReturnsMarkupUnmodifiedWithUnchangedShape() throws Exception {
        QuizService.Recap recap = new QuizService.Recap(subtopicId, "Light reactions", snack);
        when(quizService.getQuizSummary(any())).thenReturn(
                new QuizService.QuizSummary(subtopicId, 4, 3, 30, List.of(), recap));

        mockMvc.perform(get("/api/quiz/summary/{subtopicId}", subtopicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recap.snack.concept").value(CONCEPT))
                .andExpect(jsonPath("$.recap.snack.metaphor").value(METAPHOR))
                .andExpect(jsonPath("$.recap.snack.keywords[0].term").value("mitosis"))
                .andExpect(jsonPath("$.recap.snack.length()").value(8));
    }

    @Test
    void historyResponseReturnsMarkupUnmodifiedWithUnchangedShape() throws Exception {
        ThemeService.SnackHistory snackHistory = new ThemeService.SnackHistory(
                snack.getId(), CONCEPT, METAPHOR, WHY, RELATED, KEYWORDS);
        ThemeService.SubtopicHistory subtopicHistory = new ThemeService.SubtopicHistory(
                subtopicId, "Light reactions", List.of(snackHistory), List.of());
        when(themeService.listHistory()).thenReturn(List.of(new ThemeService.ThemeHistory(
                themeId, "Photosynthesis", LocalDateTime.of(2026, 9, 23, 12, 0), List.of(subtopicHistory))));

        mockMvc.perform(get("/api/themes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subtopics[0].snacks[0].concept").value(CONCEPT))
                .andExpect(jsonPath("$[0].subtopics[0].snacks[0].whyItMatters").value(WHY))
                .andExpect(jsonPath("$[0].subtopics[0].snacks[0].related[0]").value("Alpha"))
                .andExpect(jsonPath("$[0].subtopics[0].snacks[0].keywords[0].term").value("mitosis"))
                .andExpect(jsonPath("$[0].subtopics[0].snacks[0].keywords[1].definition").value("energy <currency>"))
                // History SnackItem exposes exactly 6 fields (id, concept, metaphor, whyItMatters, related, keywords).
                .andExpect(jsonPath("$[0].subtopics[0].snacks[0].length()").value(6));
    }
}
