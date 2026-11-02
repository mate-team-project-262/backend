package com.team262.moviematch.source;

import java.util.List;

/** One bounded provider page, plus metadata needed for random page selection. */
public record DiscoverPage(List<TitleCandidate> items, int page, int totalPages, long totalResults) {
    public DiscoverPage {
        items = List.copyOf(items);
        if (items.size() > 20 || page < 1 || page > 500 || totalPages < 0
                || totalResults < items.size() || (!items.isEmpty() && page > totalPages)) {
            throw new IllegalArgumentException("Invalid discover pagination");
        }
    }
}
