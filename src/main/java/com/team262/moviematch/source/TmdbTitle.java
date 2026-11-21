package com.team262.moviematch.source;

import com.team262.moviematch.model.ContentType;
import java.math.BigDecimal;
import java.util.List;

public record TmdbTitle(Long tmdbId, ContentType mediaType, String title, Integer releaseYear,
                        String overview, String posterPath, BigDecimal popularity,
                        BigDecimal voteAverage, Integer runtimeMinutes, List<TitleGenre> genres,
                        String director, List<String> creators, List<String> cast, String ageRating,
                        Long budget, Long revenue, String originalLanguage,
                        List<CastPhoto> castPhotos) {
    public TmdbTitle {
        genres = genres == null ? List.of() : List.copyOf(genres);
        creators = creators == null ? List.of() : List.copyOf(creators);
        cast = cast == null ? List.of() : List.copyOf(cast);
        castPhotos = castPhotos == null ? List.of() : List.copyOf(castPhotos);
    }

    public TmdbTitle(Long tmdbId, ContentType mediaType, String title, Integer releaseYear,
                     String overview, String posterPath, BigDecimal popularity,
                     BigDecimal voteAverage, Integer runtimeMinutes, List<TitleGenre> genres,
                     String director, List<String> creators, List<String> cast, String ageRating,
                     Long budget, Long revenue, String originalLanguage) {
        this(tmdbId, mediaType, title, releaseYear, overview, posterPath, popularity,
                voteAverage, runtimeMinutes, genres, director, creators, cast, ageRating,
                budget, revenue, originalLanguage, List.of());
    }

    public TitleCandidate candidate() {
        return new TitleCandidate(tmdbId, mediaType, title, releaseYear, overview, posterPath, popularity);
    }
}
