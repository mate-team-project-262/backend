package com.team262.moviematch.config.tmdb;

import com.team262.moviematch.client.TmdbClient;
import com.team262.moviematch.source.StubTitleSource;
import com.team262.moviematch.source.TitleSource;
import com.team262.moviematch.source.TmdbTitleSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TitleSourceConfiguration {
    @Bean
    public TitleSource titleSource(TitleSourceProperties source, TmdbClient client,
                                   TmdbProperties properties) {
        return switch (source.source()) {
            case STUB -> new StubTitleSource();
            case TMDB -> new TmdbTitleSource(client, properties);
        };
    }
}
