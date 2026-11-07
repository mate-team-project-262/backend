package com.team262.moviematch.source;

import com.team262.moviematch.exception.ex.ExternalServiceException;
import com.team262.moviematch.model.ContentType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StubTitleSourceTest {
    private final TitleSource source = new StubTitleSource();

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void providesMoreThanTwentyCompleteCardsWithStablePages(ContentType type) {
        List<TitleCandidate> first = source.discover(type, null, 1).items();
        List<TitleCandidate> second = source.discover(type, null, 2).items();
        assertThat(first).hasSize(20);
        assertThat(second).hasSize(4);
        assertThat(source.discover(type, null, 3).items()).isEmpty();
        assertThat(new StubTitleSource().discover(type, null, 1).items()).isEqualTo(first);
        Set<Long> ids = new HashSet<>();
        java.util.stream.Stream.concat(first.stream(), second.stream()).forEach(candidate -> {
            assertThat(ids.add(candidate.tmdbId())).isTrue();
            TmdbTitle title = source.getDetails(type, candidate.tmdbId());
            assertThat(title.candidate()).isEqualTo(candidate);
            assertThat(title.mediaType()).isEqualTo(type);
            assertThat(title.title()).isNotBlank();
            assertThat(title.releaseYear()).isPositive();
            assertThat(title.overview()).isNotBlank();
            assertThat(new ClassPathResource("static" + title.posterPath()).exists()).isTrue();
            assertThat(title.popularity()).isPositive();
            assertThat(title.voteAverage()).isPositive();
            assertThat(title.runtimeMinutes()).isPositive();
            assertThat(title.genres()).isNotEmpty().allSatisfy(genre -> {
                assertThat(genre.id()).isPositive();
                assertThat(genre.name()).isNotBlank();
            });
            assertThat(title.director()).isNotBlank();
            assertThat(title.creators()).isNotEmpty();
            assertThat(title.cast()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(title.ageRating()).isNotBlank();
            assertThat(title.budget()).isPositive();
            assertThat(title.revenue()).isPositive();
            assertThat(title.originalLanguage()).isNotBlank();
        });
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void honorsAllFiltersBeforePagination(ContentType type) {
        DiscoverFilters filters = new DiscoverFilters(Set.of(18, 35), 120, 1000);
        List<TitleCandidate> actual = source.discover(type, filters, 1).items();
        List<TitleCandidate> expected = java.util.stream.Stream.concat(
                        source.discover(type, null, 1).items().stream(), source.discover(type, null, 2).items().stream())
                .filter(candidate -> {
                    TmdbTitle title = source.getDetails(type, candidate.tmdbId());
                    int index = (int) (candidate.tmdbId()
                            - (type == ContentType.MOVIES ? 9000001L : 9100001L));
                    return title.genres().stream().map(TitleGenre::id).anyMatch(filters.genreIds()::contains)
                            && title.runtimeMinutes() <= 120 && (200 + index * 100) >= 1000;
                }).toList();
        assertThat(actual).isNotEmpty().isEqualTo(expected);
        assertThat(actual).anySatisfy(candidate -> assertThat(source.getDetails(type, candidate.tmdbId()).genres())
                .extracting(TitleGenre::id).contains(18).doesNotContain(35));
        assertThat(source.discover(type, new DiscoverFilters(Set.of(99999), null, null), 1).items()).isEmpty();
        assertThat(source.discover(type, new DiscoverFilters(null, null, Integer.MAX_VALUE), 1).items()).isEmpty();
        assertThat(source.discover(type, new DiscoverFilters(null, 1, null), 1).items()).isEmpty();
    }

    @Test
    void detailsAreImmutableAndUnknownIdentityIsNotFabricated() {
        TmdbTitle title = source.getDetails(ContentType.MOVIES, 9000001L);
        assertThatThrownBy(() -> title.cast().add("Changed")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> title.genres().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> source.getDetails(ContentType.TV_SERIES, title.tmdbId()))
                .isInstanceOf(ExternalServiceException.class);
        assertThatThrownBy(() -> source.getDetails(ContentType.MOVIES, Long.MAX_VALUE))
                .isInstanceOf(ExternalServiceException.class);
        assertThatThrownBy(() -> source.getDetails(ContentType.MOVIES, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> source.discover(ContentType.MOVIES, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filtersRejectInvalidValuesAndDefensivelyCopyGenres() {
        Set<Integer> genres = new HashSet<>(Set.of(18));
        DiscoverFilters filters = new DiscoverFilters(genres, null, 0);
        genres.add(35);
        assertThat(filters.genreIds()).containsExactly(18);
        assertThatThrownBy(() -> filters.genreIds().add(35)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new DiscoverFilters(Set.of(-1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiscoverFilters(null, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiscoverFilters(null, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
