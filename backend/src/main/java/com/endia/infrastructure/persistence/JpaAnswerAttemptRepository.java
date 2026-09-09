package com.endia.infrastructure.persistence;

import com.endia.domain.model.AnswerAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaAnswerAttemptRepository extends JpaRepository<AnswerAttempt, UUID> {

    List<AnswerAttempt> findByQuizQuestionId(UUID quizQuestionId);

    boolean existsByQuizQuestionId(UUID quizQuestionId);
}
