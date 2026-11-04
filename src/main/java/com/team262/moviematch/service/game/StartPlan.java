package com.team262.moviematch.service.game;

import com.team262.moviematch.model.ContentType;
import java.util.Set;

public record StartPlan(
        Long roomId,
        ContentType contentType,
        Set<Integer> genreIds,
        Integer maxDurationMinutes
) {
}
