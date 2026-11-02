package com.team262.moviematch.source;

import com.team262.moviematch.model.ContentType;
import java.util.List;

public interface TitleSource {
    /** Returns one provider page (1-based), preserving the provider's candidate order. */
    List<TitleCandidate> discover(ContentType contentType, DiscoverFilters filters, int page);

    TmdbTitle getDetails(ContentType contentType, long tmdbId);
}
