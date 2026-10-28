package com.team262.moviematch.dto.room.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Participant in the shared list; token and token issuance time are never returned")
public record ParticipantDto(
        @Schema(description = "Participant ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(description = "Display name", example = "Anna", requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(description = "Whether this participant created the room", example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean host) {
}
