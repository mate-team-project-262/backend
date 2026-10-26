package com.team262.moviematch.dto.room.request;

import com.team262.moviematch.model.ContentType;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record PreferencesRequest(
        @NotNull ContentType contentType,
        Set<@NotNull Integer> genreIds,
        Integer maxDurationMinutes
) {
}