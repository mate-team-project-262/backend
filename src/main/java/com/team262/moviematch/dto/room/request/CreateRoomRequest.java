package com.team262.moviematch.dto.room.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(min = 2, max = 30) String displayName,
        @Valid PreferencesRequest preferences) {
}