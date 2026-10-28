package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.ContentType;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

public record PreferencesResponse(
        @Schema(description = "Тип контента", example = "MOVIES", requiredMode = Schema.RequiredMode.REQUIRED) ContentType contentType,
        @Schema(description = "ID жанров, [] означает любые жанры; порядок не гарантирован", example = "[28,35]",
                requiredMode = Schema.RequiredMode.REQUIRED) Set<Integer> genreIds,
        @Schema(description = "Ограничение длительности в минутах; null означает без ограничения", nullable = true,
                example = "120", requiredMode = Schema.RequiredMode.REQUIRED) Integer maxDurationMinutes
) {
}
