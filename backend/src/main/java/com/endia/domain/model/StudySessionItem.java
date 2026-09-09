package com.endia.domain.model;

import com.endia.domain.enums.GenerationStatus;
import com.endia.domain.enums.LlmErrorCause;
import jakarta.persistence.Column;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Per-subtopic generation status within a {@link StudySession}. Tracks snack + quiz
 * status and the failure cause (FR-001..004, FR-015).
 */
@Entity
@Table(name = "study_session_item", indexes = @Index(name = "idx_study_session_item_session", columnList = "session_id"))
@Getter
@Setter
@NoArgsConstructor
public class StudySessionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StudySession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subtopic_id", nullable = false)
    private Subtopic subtopic;

    @Enumerated(EnumType.STRING)
    @Column(name = "snack_status", nullable = false, length = 20)
    private GenerationStatus snackStatus = GenerationStatus.PENDING;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snack_id", nullable = true)
    private Snack snack;

    @Enumerated(EnumType.STRING)
    @Column(name = "snack_cause", nullable = true, length = 20)
    private LlmErrorCause snackCause;

    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_status", nullable = false, length = 20)
    private GenerationStatus quizStatus = GenerationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_cause", nullable = true, length = 20)
    private LlmErrorCause quizCause;

    public StudySessionItem(StudySession session, Subtopic subtopic) {
        this.session = session;
        this.subtopic = subtopic;
    }
}
