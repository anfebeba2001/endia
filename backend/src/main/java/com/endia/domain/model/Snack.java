package com.endia.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.endia.infrastructure.persistence.JpaJsonConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "snack", indexes = @Index(name = "idx_snack_subtopic", columnList = "subtopic_id"))
@Getter
@Setter
@NoArgsConstructor
public class Snack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subtopic_id", nullable = false)
    private Subtopic subtopic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String concept;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String metaphor;

    @Column(name = "why_it_matters", nullable = false, columnDefinition = "TEXT")
    private String whyItMatters;

    @Convert(converter = JpaJsonConverter.class)
    @Column(nullable = false, columnDefinition = "JSON")
    private List<String> related = new ArrayList<>();

    public Snack(Subtopic subtopic, String concept, String metaphor, String whyItMatters, List<String> related) {
        this.subtopic = subtopic;
        this.concept = concept;
        this.metaphor = metaphor;
        this.whyItMatters = whyItMatters;
        this.related = related != null ? related : new ArrayList<>();
    }
}
