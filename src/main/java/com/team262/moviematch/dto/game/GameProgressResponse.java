package com.team262.moviematch.dto.game;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record GameProgressResponse(
        @Schema(description = "Participants who voted on every card", example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED) int finishedParticipants,
        @Schema(description = "All participants in the room, including the host", example = "4",
                requiredMode = Schema.RequiredMode.REQUIRED) int totalParticipants,
        @Schema(description = "Whether the caller has voted on every card",
                requiredMode = Schema.RequiredMode.REQUIRED) boolean myFinished,
        @Schema(description = "true when every participant has finished. The room status stays VOTING; "
                + "no winner is computed here", requiredMode = Schema.RequiredMode.REQUIRED) boolean allFinished,
        @Schema(description = "Participants ordered by ID, without tokens",
                requiredMode = Schema.RequiredMode.REQUIRED) List<ParticipantProgressDto> participants
) {
}