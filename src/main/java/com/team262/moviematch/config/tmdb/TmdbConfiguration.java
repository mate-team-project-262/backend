package com.team262.moviematch.config.tmdb;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class TmdbConfiguration {
    public static final String BEARER = "Bearer ";

    @Bean("tmdbRestClient")
    public RestClient restClient(TmdbProperties properties) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(properties.connectTimeout());
        requests.setReadTimeout(properties.readTimeout());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requests)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (!properties.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, BEARER + properties.apiKey());
        }
        return builder.build();
    }
}
