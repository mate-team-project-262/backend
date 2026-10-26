package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.model.ContentType;
import java.util.Set;

public record PreferencesResponse(
        ContentType contentType,
        Set<Integer> genreIds,
        Integer maxDurationMinutes
) {
}