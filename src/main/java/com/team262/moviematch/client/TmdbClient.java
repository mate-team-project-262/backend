package com.team262.moviematch.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.exception.ex.ExternalServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TmdbClient {
    private final RestClient restClient;
    private final int maxResponseBytes;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public TmdbClient(@Qualifier("tmdbRestClient") RestClient restClient,
                      @Value("${tmdb.max-response-bytes:4194304}") int maxResponseBytes) {
        if (maxResponseBytes < 1 || maxResponseBytes > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("TMDB response limit must be between 1 byte and 16 MiB");
        }
        this.restClient = restClient;
        this.maxResponseBytes = maxResponseBytes;
    }

    public TmdbClient(RestClient restClient) {
        this(restClient, 4 * 1024 * 1024);
    }

    public JsonNode get(String path, MultiValueMap<String, String> query) {
        try {
            JsonNode body = restClient.get()
                    .uri(builder -> builder.path(path).queryParams(query).build())
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()
                                || response.getHeaders().getContentLength() > maxResponseBytes) {
                            throw new ExternalServiceException();
                        }
                        byte[] bytes = response.getBody().readNBytes(maxResponseBytes + 1);
                        if (bytes.length > maxResponseBytes) {
                            throw new ExternalServiceException();
                        }
                        return json.readTree(bytes);
                    });
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
