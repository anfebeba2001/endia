package com.endia.interfaces.rest.dto;

/**
 * Generated short definition (feature 016, FR-004). Exactly two fields: the normalized selected
 * term and the generated definition (non-blank, at most 20 words). No id, no persisted/quiz/keyword
 * fields; nothing is persisted.
 */
public record DefinitionGenerateApiResponse(
        String term,
        String definition
) {
}
