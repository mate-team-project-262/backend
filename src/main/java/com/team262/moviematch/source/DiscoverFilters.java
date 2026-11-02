package com.team262.moviematch.source;

import java.util.Set;

public record DiscoverFilters(Set<Integer> genreIds, Integer maxDurationMinutes, Integer minVoteCount) {
    public DiscoverFilters {
        genreIds = genreIds == null ? Set.of() : Set.copyOf(genreIds);
        if (genreIds.stream().anyMatch(id -> id <= 0)
                || (maxDurationMinutes != null && maxDurationMinutes <= 0)
                || (minVoteCount != null && minVoteCount < 0)) {
            throw new IllegalArgumentException("Invalid discover filters");
        }
    }
}
