package com.team262.moviematch.dto.game;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record GameResultsResponse(
        @Schema(description = "Number of participants at the end of the round", example = "4") int totalParticipants,
        List<ResultCardResponse> cards
) {
}
