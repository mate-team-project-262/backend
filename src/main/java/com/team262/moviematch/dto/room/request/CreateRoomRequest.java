package com.team262.moviematch.dto.room.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateRoomRequest(
        @Schema(description = "Host display name: non-blank, 3-20 characters", example = "Anna", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 3, max = 20) String displayName,
        @Schema(description = "Preferences. If omitted or null: MOVIES, an empty genre set and a null duration limit.", nullable = true)
        @Valid PreferencesRequest preferences) {
}
