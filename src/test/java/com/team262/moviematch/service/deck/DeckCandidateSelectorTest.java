package com.team262.moviematch.service.deck;

import com.team262.moviematch.config.deck.DeckProperties;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.DeckBuildException;
import com.team262.moviematch.exception.ex.ExternalServiceException;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.source.DiscoverFilters;
import com.team262.moviematch.source.DiscoverPage;
import com.team262.moviematch.source.StubTitleSource;
import com.team262.moviematch.source.TitleCandidate;
import com.team262.moviematch.source.TitleGenre;
import com.team262.moviematch.source.TitleSource;
import com.team262.moviematch.source.TmdbTitle;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeckCandidateSelectorTest {
    private final DiscoverFilters filters = new DiscoverFilters(Set.of(18), 120, null);

    static DeckProperties properties() {
        return new DeckProperties(50, 4, 8, 30);
    }

    private DeckCandidateSelector selector(TitleSource source, DeckProperties props) {
        return new DeckCandidateSelector(source, props);
    }

    @Test
    void samplesFourDifferentPagesWithinWindowAndFetchesOnlyTwentyDetails() {
        FakeSource source = new FakeSource(900);
        DeckProperties props = properties();
        List<TmdbTitle> result = selector(source, props).select(ContentType.MOVIES,
                filters, new Random(42));
        assertThat(result).hasSize(20);
        assertThat(result).extracting(TmdbTitle::tmdbId).doesNotHaveDuplicates();
        assertThat(result.stream().collect(Collectors.groupingBy(t -> t.tmdbId() / 100,
                Collectors.counting())).values()).containsExactlyInAnyOrder(5L, 5L, 5L, 5L);
        assertThat(source.pages).allMatch(page -> page >= 1 && page <= 50);
        assertThat(source.discoverCalls.get()).isBetween(4, 5);
        assertThat(source.detailCalls.get()).isEqualTo(20);
        assertThat(source.maxActive.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void handlesSinglePageAndBothMediaTypes(ContentType type) {
        FakeSource source = new FakeSource(1);
        DeckProperties props = properties();
        List<TmdbTitle> result = selector(source, props).select(type, filters, new Random(1));
        assertThat(result).hasSize(20);
        assertThat(source.discoverCalls.get()).isEqualTo(1);
        assertThat(result).allSatisfy(title -> assertThat(title.mediaType())
                .isEqualTo(type));
    }

    @Test
    void offlineCatalogWithPartialSecondPageStillProducesFullDeck() {
        DeckProperties props = properties();
        List<TmdbTitle> result = selector(new StubTitleSource(), props).select(
                ContentType.MOVIES, new DiscoverFilters(Set.of(), null, null), new Random(5));
        assertThat(result).hasSize(20).extracting(TmdbTitle::tmdbId).doesNotHaveDuplicates();
    }

    @Test
    void invalidDetailsAreReplacedWithoutExceedingBudget() {
        FakeSource source = new FakeSource(50);
        AtomicInteger remainingBad = new AtomicInteger(4);
        source.detail = id -> card(id, ContentType.MOVIES, remainingBad.getAndDecrement() > 0 ? 999 : 90);
        DeckProperties props = properties();
        List<TmdbTitle> result = selector(source, props).select(ContentType.MOVIES,
                filters, new Random(42));
        assertThat(result).hasSize(20).allSatisfy(card ->
                assertThat(card.runtimeMinutes()).isLessThanOrEqualTo(120));
        assertThat(source.detailCalls.get()).isEqualTo(24);
    }

    @Test
    void candidatesWithoutPosterOrOverviewAreSkippedBeforeFetchingDetails() {
        FakeSource source = new FakeSource(2) {
            @Override
            public DiscoverPage discover(ContentType type, DiscoverFilters filters, int page) {
                DiscoverPage response = super.discover(type, filters, page);
                List<TitleCandidate> candidates = response.items().stream().map(candidate ->
                        new TitleCandidate(candidate.tmdbId(), candidate.mediaType(), candidate.title(),
                                candidate.releaseYear(), candidate.tmdbId() % 100 == 0 ? null : "Overview",
                                candidate.tmdbId() % 100 == 1 ? " " : "/poster.jpg", candidate.popularity()))
                        .toList();
                return new DiscoverPage(candidates, page, 2, 40);
            }
        };
        List<TmdbTitle> result = selector(source, properties()).select(ContentType.MOVIES, filters, new Random(1));
        assertThat(result).hasSize(20).allSatisfy(card ->
                assertThat(card.tmdbId() % 100).isGreaterThan(1));
        assertThat(source.detailCalls.get()).isEqualTo(20);
    }

    @Test
    void rejectsDuplicateCandidatesAndBoundsFailedSelection() {
        FakeSource source = new FakeSource(50) {
            @Override
            public DiscoverPage discover(ContentType type, DiscoverFilters filters, int page) {
                super.discover(type, filters, page);
                return new DiscoverPage(List.of(card(123, ContentType.MOVIES, 90).candidate()),
                        page, 50, 1000);
            }
        };
        DeckProperties props = properties();
        assertThatThrownBy(() -> selector(source, props).select(ContentType.MOVIES,
                filters, new Random(42)))
                .isInstanceOf(DeckBuildException.class);
        assertThat(source.detailCalls.get()).isEqualTo(1);
        assertThat(source.discoverCalls.get()).isLessThanOrEqualTo(8);
    }

    @Test
    void allUnsuitableDetailsStopAfterThirtyRequests() {
        FakeSource source = new FakeSource(50);
        source.detail = id -> card(id, ContentType.MOVIES, 999);
        DeckProperties props = properties();
        assertThatThrownBy(() -> selector(source, props).select(ContentType.MOVIES,
                filters, new Random(42)))
                .isInstanceOf(DeckBuildException.class)
                .extracting("code").isEqualTo(ErrorCode.DECK_SELECTION_LIMIT);
        assertThat(source.detailCalls.get()).isEqualTo(30);
        assertThat(source.discoverCalls.get()).isLessThanOrEqualTo(8);
    }

    @Test
    void providerFailureAbortsRatherThanSubstitutingStubData() {
        FakeSource source = new FakeSource(50);
        source.detail = id -> { throw new ExternalServiceException(); };
        DeckProperties props = properties();
        assertThatThrownBy(() -> selector(source, props).select(ContentType.MOVIES,
                filters, new Random(42)))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void tooFewResultsIsDifferentFromExhaustingTheBudget() {
        TitleSource source = new StubTitleSource();
        DeckProperties props = properties();
        assertThatThrownBy(() -> selector(source, props).select(ContentType.MOVIES,
                new DiscoverFilters(Set.of(28), null, null), new Random(1)))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo(ErrorCode.NOT_ENOUGH_TITLES);
    }

    @Test
    void detailRequestsRunSequentiallyOnTheCallingThread() {
        FakeSource source = new FakeSource(1);
        Thread caller = Thread.currentThread();
        source.detail = id -> {
            assertThat(Thread.currentThread()).isSameAs(caller);
            return card(id, ContentType.MOVIES, 90);
        };
        DeckProperties props = properties();
        assertThat(selector(source, props).select(ContentType.MOVIES, filters, new Random(1))).hasSize(20);
        assertThat(source.maxActive.get()).isEqualTo(1);
    }

    @Test
    void differentSeedsChangeMembershipNotOnlyOrder() {
        FakeSource source = new FakeSource(50);
        DeckProperties props = properties();
        DeckCandidateSelector selector = selector(source, props);
        Set<Long> one = selector.select(ContentType.MOVIES, filters, new Random(1)).stream()
                .map(TmdbTitle::tmdbId).collect(Collectors.toSet());
        Set<Long> two = selector.select(ContentType.MOVIES, filters, new Random(2)).stream()
                .map(TmdbTitle::tmdbId).collect(Collectors.toSet());
        assertThat(one).isNotEqualTo(two);
    }

    static TmdbTitle card(long id, ContentType type, Integer runtime) {
        return new TmdbTitle(id, type, "Card " + id, 2020, "Overview", "/poster.jpg",
                BigDecimal.TEN, new BigDecimal("8.5"), runtime, List.of(new TitleGenre(18, "Drama")),
                "Director", List.of("Creator"), List.of("Actor A", "Actor B"), "PG-13",
                1000000L, 5000000L, "en");
    }

    static class FakeSource implements TitleSource {
        private final int pageCount;
        final Set<Integer> pages = ConcurrentHashMap.newKeySet();
        final AtomicInteger discoverCalls = new AtomicInteger();
        final AtomicInteger detailCalls = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maxActive = new AtomicInteger();
        volatile LongFunction<TmdbTitle> detail;

        FakeSource(int pageCount) {
            this.pageCount = pageCount;
        }

        @Override
        public DiscoverPage discover(ContentType type, DiscoverFilters filters, int page) {
            discoverCalls.incrementAndGet();
            pages.add(page);
            ContentType media = type;
            return new DiscoverPage(IntStream.range(0, 20).mapToObj(i ->
                    card(page * 100L + i, media, 90).candidate()).toList(), page,
                    pageCount, pageCount * 20L);
        }

        @Override
        public TmdbTitle getDetails(ContentType type, long id) {
            detailCalls.incrementAndGet();
            int concurrent = active.incrementAndGet();
            maxActive.accumulateAndGet(concurrent, Math::max);
            try {
                return detail == null ? card(id,
                        type, 90) : detail.apply(id);
            } finally {
                active.decrementAndGet();
            }
        }
    }
}
