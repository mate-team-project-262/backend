package com.team262.moviematch.config.tmdb;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class TmdbConfiguration {
    public static final String BEARER = "Bearer ";

    @Bean
    public RestClient restClient(TmdbProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, BEARER + properties.apiKey())
                .build();
    }
}
