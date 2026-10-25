package com.team262.moviematch.dto.room.request;

import com.team262.moviematch.model.ContentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PreferencesRequest(
        @NotNull ContentType contentType,
        @Size(max = 5) List<@NotNull Integer> genreIds,
        @Min(30) @Max(300) Integer maxDurationMinutes,
        @Size(max = 5) List<@NotNull Integer> streamingServiceIds) {
}