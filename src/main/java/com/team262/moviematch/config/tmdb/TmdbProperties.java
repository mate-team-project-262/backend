package com.team262.moviematch.config.tmdb;

import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "tmdb")
public record TmdbProperties(String baseUrl, String apiKey, Duration connectTimeout,
                             Duration readTimeout, String language, String certificationCountry) {
    @ConstructorBinding
    public TmdbProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.themoviedb.org/3" : baseUrl.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        language = language == null || language.isBlank() ? "en-US" : language.trim();
        certificationCountry = certificationCountry == null || certificationCountry.isBlank()
                ? "US" : certificationCountry.trim().toUpperCase(Locale.ROOT);
        if (connectTimeout.toMillis() < 1 || connectTimeout.toMillis() > Integer.MAX_VALUE
                || readTimeout.toMillis() < 1 || readTimeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("TMDB timeouts must be between 1ms and 2147483647ms");
        }
        if (!certificationCountry.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("TMDB certification country must be a two-letter code");
        }
    }

    public TmdbProperties(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, null, null, null, null);
    }

    @Override
    public String toString() {
        return "TmdbProperties[apiKey=<redacted>, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + ", language=" + language
                + ", certificationCountry=" + certificationCountry + "]";
    }
}
