package com.team262.moviematch.config;

import com.team262.moviematch.config.tmdb.TmdbConfiguration;
import com.team262.moviematch.config.tmdb.TmdbProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Integration test requires TMDB API token")
class TmdbIntegrationTest {

    @Test
    void shouldConnectToTmdbAndRetrieveMovie() {

        String baseUrl = "https://api.themoviedb.org/3";
        String apiKey = System.getenv("TMDB_API_KEY");

        assertNotNull(apiKey, "TMDB token must be configured");
        assertFalse(apiKey.isBlank(), "TMDB token must not be blank");

        TmdbProperties properties =
                new TmdbProperties(baseUrl, apiKey);

        RestClient restClient =
                new TmdbConfiguration().restClient(properties);

        String response = restClient.get()
                .uri("/movie/550")
                .retrieve()
                .body(String.class);

        assertNotNull(response);
        assertFalse(response.isBlank());
        assertTrue(response.contains("\"id\":550"));
        assertTrue(response.contains("\"title\""));
    }
}
