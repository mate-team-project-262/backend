package com.team262.moviematch.source;

import com.team262.moviematch.model.MediaType;
import java.math.BigDecimal;

public record TitleCandidate(Long tmdbId, MediaType mediaType, String title, Integer releaseYear,
                             String overview, String posterPath, BigDecimal popularity) {
}
