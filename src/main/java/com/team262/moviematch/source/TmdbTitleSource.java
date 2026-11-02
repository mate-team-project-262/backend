package com.team262.moviematch.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.team262.moviematch.client.TmdbClient;
import com.team262.moviematch.config.tmdb.TmdbProperties;
import com.team262.moviematch.exception.ex.ExternalServiceException;
import com.team262.moviematch.model.ContentType;
import java.net.URI;
import java.util.stream.Collectors;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class TmdbTitleSource implements TitleSource {
    private final TmdbClient client;
    private final TmdbProperties properties;
    private final TmdbTitleMapper mapper;

    public TmdbTitleSource(TmdbClient client, TmdbProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalArgumentException("TMDB_API_KEY (API Read Access Token) is required in tmdb mode");
        }
        URI baseUri = URI.create(properties.baseUrl());
        if (baseUri.getHost() == null
                || !("https".equals(baseUri.getScheme()) || "http".equals(baseUri.getScheme()))) {
            throw new IllegalArgumentException("TMDB base URL must be an absolute HTTP(S) URL");
        }
        this.client = client;
        this.properties = properties;
        this.mapper = new TmdbTitleMapper(properties.certificationCountry());
    }

    @Override
    public DiscoverPage discover(ContentType contentType, DiscoverFilters filters, int page) {
        ContentType mediaType = java.util.Objects.requireNonNull(contentType, "contentType");
        TitleSourceSupport.checkPage(page);
        DiscoverFilters effective = TitleSourceSupport.filtersOrEmpty(filters);
        MultiValueMap<String, String> query = baseQuery();
        query.add("page", Integer.toString(page));
        query.add("sort_by", "popularity.desc");
        query.add("include_adult", "false");
        if (!effective.genreIds().isEmpty()) {
            query.add("with_genres", effective.genreIds().stream().sorted()
                    .map(String::valueOf).collect(Collectors.joining(",")));
        }
        if (effective.maxDurationMinutes() != null) {
            query.add("with_runtime.lte", effective.maxDurationMinutes().toString());
        }
        if (effective.minVoteCount() != null) {
            query.add("vote_count.gte", effective.minVoteCount().toString());
        }
        JsonNode response = client.get("/discover/" + path(mediaType), query);
        try {
            return mapper.page(response, mediaType, page);
        } catch (IllegalArgumentException ex) {
            throw new ExternalServiceException(ex);
        }
    }

    @Override
    public TmdbTitle getDetails(ContentType contentType, long tmdbId) {
        ContentType mediaType = java.util.Objects.requireNonNull(contentType, "contentType");
        TitleSourceSupport.checkId(tmdbId);
        MultiValueMap<String, String> query = baseQuery();
        query.add("append_to_response", mediaType == ContentType.MOVIES
                ? "credits,release_dates" : "aggregate_credits,content_ratings");
        JsonNode response = client.get("/" + path(mediaType) + "/" + tmdbId, query);
        try {
            TmdbTitle title = mapper.details(response, mediaType);
            if (title.tmdbId() != tmdbId) {
                throw new IllegalArgumentException("Unexpected TMDB identity");
            }
            return title;
        } catch (IllegalArgumentException ex) {
            throw new ExternalServiceException(ex);
        }
    }

    private MultiValueMap<String, String> baseQuery() {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("language", properties.language());
        return query;
    }

    private String path(ContentType type) {
        return type == ContentType.MOVIES ? "movie" : "tv";
    }
}
