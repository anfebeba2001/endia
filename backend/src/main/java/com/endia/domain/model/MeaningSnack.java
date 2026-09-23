package com.endia.domain.model;

import com.endia.domain.enums.SnackKind;
import com.endia.infrastructure.persistence.JpaJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A snack derived from a keyword definition (feature 014). It shares the regular snack reading
 * shape ({@code concept/metaphor/whyItMatters/related}) but is a <strong>distinct aggregate</strong>
 * with its own table, repository, service and endpoint group, so it can never enter the
 * batch/quiz/results/history paths (FR-014..FR-016, research.md R1/R5).
 *
 * <p>It is attached to a {@link Theme} and linked to its source {@link Subtopic} for placement in
 * the backbone (Clarification #3). The relationship is one-directional {@code @ManyToOne} only:
 * {@code Theme}/{@code Subtopic} are not modified, so their fetch/cascade behaviour is untouched.
 *
 * <p><strong>No quiz fields.</strong> There is no quiz association, no questions and no quiz
 * status — a meaning snack is structurally incapable of carrying a quiz (FR-014).
 */
@Entity
@Table(name = "meaning_snack",
        indexes = {
                @Index(name = "idx_meaning_snack_theme", columnList = "theme_id"),
                @Index(name = "idx_meaning_snack_subtopic", columnList = "source_subtopic_id"),
                @Index(name = "ux_meaning_snack_source", columnList = "source_subtopic_id, source_term", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
public class MeaningSnack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theme_id", nullable = false)
    private Theme theme;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_subtopic_id", nullable = false)
    private Subtopic sourceSubtopic;

    @Column(name = "source_term", nullable = false, length = 500)
    private String sourceTerm;

    @Column(name = "source_definition", nullable = false, length = 200)
    private String sourceDefinition;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String concept;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String metaphor;

    @Column(name = "why_it_matters", nullable = false, columnDefinition = "TEXT")
    private String whyItMatters;

    @Convert(converter = JpaJsonConverter.class)
    @Column(nullable = false, columnDefinition = "JSON")
    private List<String> related = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SnackKind kind = SnackKind.MEANING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MeaningSnack(Theme theme, Subtopic sourceSubtopic, String sourceTerm, String sourceDefinition,
                        String concept, String metaphor, String whyItMatters, List<String> related) {
        this.theme = theme;
        this.sourceSubtopic = sourceSubtopic;
        this.sourceTerm = sourceTerm;
        this.sourceDefinition = sourceDefinition;
        this.concept = concept;
        this.metaphor = metaphor;
        this.whyItMatters = whyItMatters;
        this.related = related != null ? related : new ArrayList<>();
        this.kind = SnackKind.MEANING;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
