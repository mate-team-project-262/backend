package com.team262.moviematch.dto.room.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateRoomRequest(
        @Schema(description = "Имя хоста: не пустое, 3–20 символов", example = "Anna", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 3, max = 20) String displayName,
        @Schema(description = "Настройки. Если отсутствуют или null: MOVIES, пустые жанры и длительность null.", nullable = true)
        @Valid PreferencesRequest preferences) {
}
