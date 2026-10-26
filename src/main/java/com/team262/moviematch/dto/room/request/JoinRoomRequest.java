package com.team262.moviematch.dto.room.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinRoomRequest(
        @NotBlank @Size(min = 3, max = 20)
        String displayName
) {
}
