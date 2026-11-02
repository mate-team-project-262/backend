package com.team262.moviematch.source;

import com.team262.moviematch.client.TmdbClient;
import com.team262.moviematch.exception.ex.ExternalServiceException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TmdbResponseLimitTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void oversizedBodiesAreRejectedWithOrWithoutContentLength(boolean contentLength) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://tmdb.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var response = withSuccess("{\"overview\":\"" + "x".repeat(2000) + "\"}", MediaType.APPLICATION_JSON);
        if (contentLength) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentLength(2015);
            response.headers(headers);
        }
        server.expect(requestTo("https://tmdb.example/movie/42")).andRespond(response);
        TmdbClient client = new TmdbClient(builder.build(), 100);
        assertThatThrownBy(() -> client.get("/movie/42", new LinkedMultiValueMap<>()))
                .isInstanceOf(ExternalServiceException.class);
        server.verify();
    }
}
