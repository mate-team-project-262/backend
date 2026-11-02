package com.team262.moviematch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.client.TmdbClient;
import com.team262.moviematch.config.tmdb.TmdbConfiguration;
import com.team262.moviematch.config.tmdb.TmdbProperties;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.source.DiscoverFilters;
import com.team262.moviematch.source.TitleCandidate;
import com.team262.moviematch.source.TitleSource;
import com.team262.moviematch.source.TmdbTitle;
import com.team262.moviematch.source.TmdbTitleSource;
import com.team262.moviematch.config.deck.DeckProperties;
import com.team262.moviematch.service.deck.DeckCandidateSelector;
import com.team262.moviematch.service.deck.DeckDeadline;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.team262.moviematch.source.DiscoverPage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TMDB_LIVE_TESTS", matches = "true")
class TmdbIntegrationTest {

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void discoversAndLoadsRealCardDetails(ContentType contentType) throws IOException {
        String apiKey = System.getenv("TMDB_API_KEY");
        assertThat(apiKey).as("TMDB_API_KEY must be configured for explicitly enabled live tests")
                .isNotBlank();
        TmdbProperties properties = new TmdbProperties("https://api.themoviedb.org/3", apiKey);
        TitleSource source = new TmdbTitleSource(new TmdbClient(
                new TmdbConfiguration().restClient(properties)), properties);
        boolean movie = contentType == ContentType.MOVIES;
        List<TitleCandidate> candidates = source.discover(contentType,
                new DiscoverFilters(Set.of(18), movie ? 180 : 60, 100), 1).items();
        assertThat(candidates).isNotEmpty().allSatisfy(candidate -> {
            assertThat(candidate.tmdbId()).isPositive();
            assertThat(candidate.title()).isNotBlank();
            assertThat(candidate.mediaType()).isEqualTo(movie ? ContentType.MOVIES : ContentType.TV_SERIES);
        });

        long id = movie ? 550 : 1396;
        TmdbTitle title = source.getDetails(contentType, id);
        assertThat(title.tmdbId()).isEqualTo(id);
        assertThat(title.mediaType()).isEqualTo(movie ? ContentType.MOVIES : ContentType.TV_SERIES);
        assertThat(title.title()).isNotBlank();
        assertThat(title.overview()).isNotBlank();
        assertThat(title.releaseYear()).isPositive();
        assertThat(title.posterPath()).isNotBlank();
        assertThat(title.voteAverage()).isNotNull();
        assertThat(title.runtimeMinutes()).isPositive();
        assertThat(title.genres()).isNotEmpty();
        assertThat(title.cast()).isNotEmpty();
        assertThat(title.ageRating()).isNotBlank();
        if (movie) {
            assertThat(title.director()).isNotBlank();
            assertThat(title.budget()).isPositive();
            assertThat(title.revenue()).isPositive();
        } else {
            assertThat(title.creators()).isNotEmpty();
        }
        System.out.printf("TMDB live %s: candidates=%d, detailId=%d, genres=%d, cast=%d, runtime=%d%n",
                contentType, candidates.size(), title.tmdbId(), title.genres().size(),
                title.cast().size(), title.runtimeMinutes());
        writeOptionalSnapshot(contentType, candidates, title);
    }

    private void writeOptionalSnapshot(ContentType contentType, List<TitleCandidate> candidates,
                                       TmdbTitle details) throws IOException {
        String directory = System.getenv("TMDB_LIVE_OUTPUT_DIR");
        if (directory == null || directory.isBlank()) {
            return;
        }
        Path output = Path.of(directory);
        Files.createDirectories(output);
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("contentType", contentType);
        snapshot.put("filters", new DiscoverFilters(Set.of(18),
                contentType == ContentType.MOVIES ? 180 : 60, 100));
        snapshot.put("page", 1);
        snapshot.put("candidateCount", candidates.size());
        snapshot.put("candidates", candidates);
        snapshot.put("details", details);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                output.resolve(contentType.name().toLowerCase(java.util.Locale.ROOT) + ".json").toFile(),
                snapshot);
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    @EnabledIfEnvironmentVariable(named = "DECK_LIVE_TESTS", matches = "true")
    void assemblesRealRandomDeckWithinRequestBudgets(ContentType type) {
        var properties = new TmdbProperties("https://api.themoviedb.org/3", System.getenv("TMDB_API_KEY"));
        TitleSource real = new TmdbTitleSource(new TmdbClient(
                new TmdbConfiguration().restClient(properties)), properties);
        Set<Integer> pages = ConcurrentHashMap.newKeySet();
        AtomicInteger discovers = new AtomicInteger();
        AtomicInteger details = new AtomicInteger();
        TitleSource counted = new TitleSource() {
            @Override
            public DiscoverPage discover(ContentType content, DiscoverFilters filters, int page) {
                discovers.incrementAndGet();
                pages.add(page);
                return real.discover(content, filters, page);
            }

            @Override
            public TmdbTitle getDetails(ContentType content, long id) {
                details.incrementAndGet();
                return real.getDetails(content, id);
            }
        };
        var limits = new DeckProperties(50, 4, 8, 30, Duration.ofSeconds(60));
        var selector = new DeckCandidateSelector(counted, limits);
        List<TmdbTitle> deck = selector.select(type, new DiscoverFilters(Set.of(18),
                type == ContentType.MOVIES ? 180 : 60, 100),
                new DeckDeadline(limits.buildTimeout()), new Random(42));
        assertThat(deck).hasSize(20).extracting(TmdbTitle::tmdbId).doesNotHaveDuplicates();
        assertThat(discovers.get()).isLessThanOrEqualTo(8);
        assertThat(details.get()).isBetween(20, 30);
        assertThat(pages).allMatch(page -> page >= 1 && page <= 50);
        System.out.printf("TMDB random deck %s: pages=%s, discover=%d, details=%d, cards=%d%n",
                type, pages, discovers.get(), details.get(), deck.size());
    }
}
