package com.team262.moviematch.dto.game;

import com.team262.moviematch.dto.voc.GenreDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DeckCardResponse(
        Long roomTitleId,
        int position,
        String title,
        Integer year,
        String overview,
        String posterUrl,
        Integer runtimeMinutes,
        List<GenreDto> genres,
        Double voteAverage,
        String director,
        List<String> creators,
        List<String> cast,
        String ageRating,
        Long budget,
        Long revenue,
        String originalLanguage,
        @Schema(description = "true = like, false = skip, null = not voted yet",
                nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean myVote) {
}
