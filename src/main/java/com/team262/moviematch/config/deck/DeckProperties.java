package com.team262.moviematch.config.deck;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "deck")
public record DeckProperties(
        @DefaultValue("50") int maxPageWindow,
        @DefaultValue("4") int initialPageCount,
        @DefaultValue("8") int maxDiscoverRequests,
        @DefaultValue("30") int maxDetailsRequests) {
    public static final int SIZE = 20;

    public DeckProperties {
        if (maxPageWindow < 1 || maxPageWindow > 500 || initialPageCount < 1
                || initialPageCount > maxPageWindow || maxDiscoverRequests < initialPageCount + 1
                || maxDiscoverRequests > 16 || maxDetailsRequests < SIZE || maxDetailsRequests > 60) {
            throw new IllegalArgumentException("Invalid deck resource limits");
        }
    }
}
