package com.team262.moviematch.dto.game;

import com.team262.moviematch.dto.voc.GenreDto;
import java.util.List;

public record DeckCardDetails(
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
        String originalLanguage
) {
}
