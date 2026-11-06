package com.team262.moviematch.dto.game;

import io.swagger.v3.oas.annotations.media.Schema;

public record ParticipantProgressDto(
        @Schema(description = "Participant ID; the frontend marks (you) by comparing it with its own ID",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(description = "Display name", example = "Sofia",
                requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(description = "Whether this participant is the host",
                requiredMode = Schema.RequiredMode.REQUIRED) boolean host,
        @Schema(description = "true = Done (voted on every card), false = Swiping...",
                requiredMode = Schema.RequiredMode.REQUIRED) boolean finished
) {
}