package com.team262.moviematch.dto.game;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;

public record ResultCardResponse(
        @JsonUnwrapped DeckCardDetails card,
        @Schema(description = "Likes at the end of the round", example = "3") int likesCount,
        @Schema(description = "Whether this card shares first place") boolean winner
) {
}
