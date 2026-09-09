package com.endia.infrastructure.persistence;

import com.endia.domain.model.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaQuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findBySubtopicId(UUID subtopicId);
}
