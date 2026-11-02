package com.team262.moviematch.source;

import com.team262.moviematch.model.ContentType;
import java.math.BigDecimal;

public record TitleCandidate(Long tmdbId, ContentType mediaType, String title, Integer releaseYear,
                             String overview, String posterPath, BigDecimal popularity) {
}
