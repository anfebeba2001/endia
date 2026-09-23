package com.endia.interfaces.rest.dto;

import com.endia.domain.model.SnackKeyword;

/**
 * API representation of a snack keyword (feature 013, FR-006): a plain-text {@code term} with a
 * very short {@code definition} (at most 3 words, guaranteed by service normalization).
 */
public record SnackKeywordDto(String term, String definition) {

    public static SnackKeywordDto from(SnackKeyword keyword) {
        return new SnackKeywordDto(keyword.term(), keyword.definition());
    }
}
