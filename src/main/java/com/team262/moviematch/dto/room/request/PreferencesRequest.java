package com.team262.moviematch.dto.room.request;

import com.team262.moviematch.model.ContentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

public record PreferencesRequest(
        @Schema(description = "Content type; required when the preferences object is supplied", example = "MOVIES",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull ContentType contentType,
        @Schema(description = "External catalog genre IDs. Omitted, null or an empty array means any genre. "
                + "Duplicates are removed, null elements are rejected and order is irrelevant.", example = "[28,35]", nullable = true)
        Set<@NotNull Integer> genreIds,
        @Schema(description = "Maximum duration: any integer from 30 through 150 inclusive; "
                + "omitted or null means no limit. The 90/120/150 buttons are UI choices, not an API enum.",
                example = "120", nullable = true)
        @Min(30) @Max(150) Integer maxDurationMinutes
) {
}
