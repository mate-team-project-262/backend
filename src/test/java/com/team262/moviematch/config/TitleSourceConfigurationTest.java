package com.team262.moviematch.config;

import com.team262.moviematch.client.TmdbClient;
import com.team262.moviematch.config.tmdb.TitleSourceConfiguration;
import com.team262.moviematch.config.tmdb.TitleSourceProperties;
import com.team262.moviematch.config.tmdb.TmdbConfiguration;
import com.team262.moviematch.config.tmdb.TmdbProperties;
import com.team262.moviematch.source.StubTitleSource;
import com.team262.moviematch.source.TitleSource;
import com.team262.moviematch.source.TmdbTitleSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TitleSourceConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withPropertyValues("tmdb.api-key=")
            .withUserConfiguration(Properties.class, TmdbConfiguration.class,
                    TmdbClient.class, TitleSourceConfiguration.class);

    @Test
    void defaultsToExactlyOneOfflineSourceWithoutCredentials() {
        context.run(app -> {
            assertThat(app).hasNotFailed().hasSingleBean(TitleSource.class);
            assertThat(app.getBean(TitleSource.class)).isInstanceOf(StubTitleSource.class);
        });
    }

    @Test
    void explicitlySelectsRealSourceAndBindsTimeoutLanguageAndCountry() {
        context.withPropertyValues("title.source=tmdb", "tmdb.api-key=test-read-token",
                "tmdb.connect-timeout=250ms", "tmdb.read-timeout=2s",
                "tmdb.language=uk-UA", "tmdb.certification-country=gb").run(app -> {
                    assertThat(app).hasNotFailed().hasSingleBean(TitleSource.class);
                    assertThat(app.getBean(TitleSource.class)).isInstanceOf(TmdbTitleSource.class);
                    TmdbProperties properties = app.getBean(TmdbProperties.class);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(250));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.language()).isEqualTo("uk-UA");
                    assertThat(properties.certificationCountry()).isEqualTo("GB");
                    assertThat(properties.toString()).doesNotContain("test-read-token");
                });
    }

    @Test
    void realModeRequiresTokenAndNeverFallsBackToStub() {
        context.withPropertyValues("title.source=tmdb").run(app -> assertThat(app).hasFailed());
        context.withPropertyValues("title.source=tmdb", "tmdb.api-key=   ")
                .run(app -> assertThat(app).hasFailed());
    }

    @Test
    void typoInModeInvalidUrlAndInfiniteTimeoutFailConfiguration() {
        context.withPropertyValues("title.source=typo").run(app -> assertThat(app).hasFailed());
        context.withPropertyValues("title.source=tmdb", "tmdb.api-key=test", "tmdb.base-url=invalid")
                .run(app -> assertThat(app).hasFailed());
        context.withPropertyValues("tmdb.read-timeout=0").run(app -> assertThat(app).hasFailed());
        assertThatThrownBy(() -> new TmdbProperties(null, null, Duration.ofNanos(1),
                Duration.ofSeconds(1), null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({TmdbProperties.class, TitleSourceProperties.class})
    static class Properties {
    }
}
