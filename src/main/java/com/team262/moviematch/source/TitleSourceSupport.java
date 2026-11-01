package com.team262.moviematch.source;

import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.model.MediaType;
import java.util.Objects;

final class TitleSourceSupport {
    private TitleSourceSupport() {
    }

    static MediaType mediaType(ContentType contentType) {
        return switch (Objects.requireNonNull(contentType, "contentType")) {
            case MOVIES -> MediaType.MOVIE;
            case TV_SERIES -> MediaType.TV;
        };
    }

    static void checkPage(int page) {
        if (page < 1 || page > 500) {
            throw new IllegalArgumentException("Discover page must be between 1 and 500");
        }
    }

    static void checkId(long tmdbId) {
        if (tmdbId <= 0) {
            throw new IllegalArgumentException("TMDB ID must be positive");
        }
    }

    static DiscoverFilters filtersOrEmpty(DiscoverFilters filters) {
        return filters == null ? new DiscoverFilters(null, null, null) : filters;
    }
}
