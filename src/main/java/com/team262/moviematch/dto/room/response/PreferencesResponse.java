package com.team262.moviematch.dto.room.response;

import com.team262.moviematch.dto.voc.GenreDto;
import com.team262.moviematch.model.ContentType;
import java.util.List;

public record PreferencesResponse(
        ContentType contentType,
        List<GenreDto> genres,
        Integer maxDurationMinutes) {
}