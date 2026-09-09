package com.endia.domain.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.endia.infrastructure.persistence.JpaJsonConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quiz_question", indexes = @Index(name = "idx_quiz_question_subtopic", columnList = "subtopic_id"))
@Getter
@Setter
@NoArgsConstructor
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subtopic_id", nullable = false)
    private Subtopic subtopic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Convert(converter = JpaJsonConverter.class)
    @Column(nullable = false, columnDefinition = "JSON")
    private List<String> options = new ArrayList<>();

    @Column(name = "correct_index", nullable = false)
    private int correctIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @OneToMany(mappedBy = "quizQuestion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AnswerAttempt> answerAttempts = new ArrayList<>();

    public QuizQuestion(Subtopic subtopic, String prompt, List<String> options, int correctIndex, String explanation) {
        this.subtopic = subtopic;
        this.prompt = prompt;
        this.options = options != null ? options : new ArrayList<>();
        this.correctIndex = correctIndex;
        this.explanation = explanation;
    }
}
