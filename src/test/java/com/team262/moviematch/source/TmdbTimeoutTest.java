package com.team262.moviematch.source;

import com.sun.net.httpserver.HttpServer;
import com.team262.moviematch.client.TmdbClient;
import com.team262.moviematch.config.tmdb.TmdbConfiguration;
import com.team262.moviematch.config.tmdb.TmdbProperties;
import com.team262.moviematch.exception.ex.ExternalServiceException;
import com.team262.moviematch.model.ContentType;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class TmdbTimeoutTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void configuredReadTimeoutAppliesToDiscoverAndDetails(boolean discover) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(executor);
            server.createContext("/3/", exchange -> {
                try {
                    Thread.sleep(800);
                    byte[] body = "{\"id\":42,\"results\":[]}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                    // The timed-out client can disconnect before the delayed response is written.
                } finally {
                    exchange.close();
                }
            });
            server.start();
            TmdbProperties properties = new TmdbProperties(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/3", "local-test-token",
                    Duration.ofMillis(100), Duration.ofMillis(100), null, null);
            TitleSource source = new TmdbTitleSource(new TmdbClient(
                    new TmdbConfiguration().restClient(properties)), properties);
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                        assertThatThrownBy(() -> {
                            if (discover) {
                                source.discover(ContentType.MOVIES, null, 1);
                            } else {
                                source.getDetails(ContentType.TV_SERIES, 42);
                            }
                        }).isInstanceOf(ExternalServiceException.class)
                                .hasMessage("TMDB is unavailable")
                                .hasRootCauseInstanceOf(SocketTimeoutException.class));
            } finally {
                server.stop(0);
            }
        }
    }
}
