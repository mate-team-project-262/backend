package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.ContentType;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

public record PreferencesResponse(
        @Schema(description = "Content type", example = "MOVIES", requiredMode = Schema.RequiredMode.REQUIRED) ContentType contentType,
        @Schema(description = "Genre IDs; an empty array means any genre. Order is not guaranteed", example = "[28,35]",
                requiredMode = Schema.RequiredMode.REQUIRED) Set<Integer> genreIds,
        @Schema(description = "Duration limit in minutes; null means no limit", nullable = true,
                example = "120", requiredMode = Schema.RequiredMode.REQUIRED) Integer maxDurationMinutes
) {
}
