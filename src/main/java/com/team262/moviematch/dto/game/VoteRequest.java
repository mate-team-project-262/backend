package com.team262.moviematch.dto.game;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VoteRequest(
        @Schema(description = "ID of a card in this room's deck", example = "41")
        @NotNull @Positive Long roomTitleId,
        @Schema(description = "true = like, false = skip; required", example = "false")
        @NotNull Boolean isLiked) {
}
