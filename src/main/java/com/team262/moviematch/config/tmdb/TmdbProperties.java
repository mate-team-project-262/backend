package com.team262.moviematch.config.tmdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tmdb")
public record TmdbProperties(
        String baseUrl,
        String apiKey
) {
}
