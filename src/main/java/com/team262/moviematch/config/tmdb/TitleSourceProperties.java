package com.team262.moviematch.config.tmdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "title")
public record TitleSourceProperties(Mode source) {
    public TitleSourceProperties {
        source = source == null ? Mode.STUB : source;
    }

    public enum Mode {
        STUB,
        TMDB
    }
}
