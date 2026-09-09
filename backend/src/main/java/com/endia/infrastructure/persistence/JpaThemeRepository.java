package com.endia.infrastructure.persistence;

import com.endia.domain.model.Theme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaThemeRepository extends JpaRepository<Theme, UUID> {
}
