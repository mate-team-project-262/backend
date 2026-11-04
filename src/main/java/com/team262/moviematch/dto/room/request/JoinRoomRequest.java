package com.team262.moviematch.dto.room.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record JoinRoomRequest(
        @Schema(description = "Participant display name: non-blank, 3-20 characters; available in the room (case-insensitive)",
                example = "Egor", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 3, max = 20)
        String displayName
) {
}
