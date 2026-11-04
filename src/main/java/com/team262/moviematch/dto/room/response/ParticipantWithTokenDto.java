package com.team262.moviematch.dto.room.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Current participant with their personal secret token; never used in the shared list")
public record ParticipantWithTokenDto(
        @Schema(description = "Participant ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(description = "Display name", example = "Anna", requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(description = "Whether this participant is the host", example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean host,
        @Schema(description = "Token valid for 24 hours. Send it in X-Participant-Token without Bearer; do not share it.",
                format = "uuid", example = "3f2b8c1e-5a4d-4e7b-9c2a-1d6f0b7e8a90",
                requiredMode = Schema.RequiredMode.REQUIRED) String token) {
}
