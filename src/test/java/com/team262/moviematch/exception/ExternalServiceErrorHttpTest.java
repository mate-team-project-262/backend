package com.team262.moviematch.exception;

import com.team262.moviematch.exception.ex.ExternalServiceException;
import com.team262.moviematch.exception.ex.DeckBuildException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExternalServiceErrorHttpTest {
    @Test
    void deckTimeoutHasASeparate503Contract() throws Exception {
        MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/test/deck"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DECK_BUILD_TIMEOUT"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }
    @Test
    void translatesExternalFailureTo502WithoutExposingProviderDetails() throws Exception {
        MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/test/tmdb"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TMDB_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("TMDB is unavailable"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private-token"))));
    }

    @RestController
    static class ProbeController {
        @GetMapping("/test/deck")
        String timeout() {
            throw new DeckBuildException(ErrorCode.DECK_BUILD_TIMEOUT, "Deck preparation timed out");
        }
        @GetMapping("/test/tmdb")
        String fail() {
            throw new ExternalServiceException(new RuntimeException("private-token upstream message"));
        }
    }
}
