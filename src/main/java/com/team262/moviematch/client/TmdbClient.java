package com.team262.moviematch.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.team262.moviematch.exception.ex.ExternalServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TmdbClient {
    private final RestClient restClient;

    public TmdbClient(@Qualifier("tmdbRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public JsonNode get(String path, MultiValueMap<String, String> query) {
        try {
            JsonNode body = restClient.get()
                    .uri(builder -> builder.path(path).queryParams(query).build())
                    .retrieve().body(JsonNode.class);
            if (body == null || !body.isObject()
                    || (body.has("success") && !body.path("success").asBoolean())) {
                throw new ExternalServiceException();
            }
            return body;
        } catch (RestClientException ex) {
            throw new ExternalServiceException(ex);
        }
    }
}
