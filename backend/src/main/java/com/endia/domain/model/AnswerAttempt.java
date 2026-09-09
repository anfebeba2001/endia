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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.endia.infrastructure.persistence.JpaJsonConverter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "answer_attempt",
    indexes = {
        @Index(name = "idx_answer_attempt_question", columnList = "quiz_question_id"),
    },
    uniqueConstraints = @UniqueConstraint(
        name = "uk_answer_attempt_question",
        columnNames = {"quiz_question_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class AnswerAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_question_id", nullable = false)
    private QuizQuestion quizQuestion;

    @Column(name = "chosen_index", nullable = true)
    private Integer chosenIndex;

    @Convert(converter = JpaJsonConverter.class)
    @Column(name = "arrangement", nullable = true, columnDefinition = "JSON")
    private List<String> arrangement = new ArrayList<>();

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "xp_earned", nullable = false)
    private int xpEarned;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AnswerAttempt(QuizQuestion quizQuestion, int chosenIndex, boolean correct, int xpEarned) {
        this.quizQuestion = quizQuestion;
        this.chosenIndex = chosenIndex;
        this.correct = correct;
        this.xpEarned = xpEarned;
    }

    public AnswerAttempt(QuizQuestion quizQuestion, List<String> arrangement, boolean correct, int xpEarned) {
        this.quizQuestion = quizQuestion;
        this.chosenIndex = null;
        this.arrangement = arrangement != null ? arrangement : new ArrayList<>();
        this.correct = correct;
        this.xpEarned = xpEarned;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
