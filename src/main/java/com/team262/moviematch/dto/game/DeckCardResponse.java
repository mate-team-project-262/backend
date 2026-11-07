package com.team262.moviematch.dto.game;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;

public record DeckCardResponse(
        @JsonUnwrapped DeckCardDetails card,
        @Schema(description = "true = like, false = skip, null = not voted yet",
                nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean myVote) {
}
