package com.team262.moviematch.source;

import com.team262.moviematch.exception.ex.ExternalServiceException;
import com.team262.moviematch.model.ContentType;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/** Deterministic fictional catalog for offline tests and demos; never calls TMDB. */
public class StubTitleSource implements TitleSource {
    private static final int PAGE_SIZE = 20;
    private static final List<String> NAMES = List.of(
            "The Last Observatory", "Letters from Tomorrow", "Harbour Lights",
            "A Map of Quiet Places", "The Clockmaker's Garden", "Northern Passage",
            "The Paper Moon", "Summer on Platform Nine", "A Different Orbit",
            "The Smallest Expedition", "Beyond the Blue Door", "The Lighthouse Orchestra",
            "Winter in Amber", "The Memory Workshop", "The Midnight Library Train",
            "Cloud Atlas Station", "A House above the Sea", "The Second Sunrise",
            "The Secret of Willow Lane", "One More Horizon", "The Unfinished Journey",
            "The Lost Frequency", "A Thousand Little Stars", "The Glass Mountain");
    private final List<DemoTitle> catalog;

    public StubTitleSource() {
        catalog = java.util.Arrays.stream(ContentType.values())
                .flatMap(type -> IntStream.range(0, NAMES.size()).mapToObj(index -> create(type, index)))
                .toList();
    }

    @Override
    public DiscoverPage discover(ContentType contentType, DiscoverFilters filters, int page) {
        ContentType type = java.util.Objects.requireNonNull(contentType, "contentType");
        TitleSourceSupport.checkPage(page);
        DiscoverFilters effective = TitleSourceSupport.filtersOrEmpty(filters);
        List<TitleCandidate> filtered = catalog.stream()
                .filter(entry -> entry.title().mediaType() == type)
                .filter(entry -> effective.genreIds().isEmpty()
                        || entry.title().genres().stream().map(TitleGenre::id).anyMatch(effective.genreIds()::contains))
                .filter(entry -> effective.maxDurationMinutes() == null
                        || entry.title().runtimeMinutes() <= effective.maxDurationMinutes())
                .filter(entry -> effective.minVoteCount() == null
                        || entry.voteCount() >= effective.minVoteCount())
                .sorted(Comparator.comparing((DemoTitle entry) -> entry.title().popularity()).reversed()
                        .thenComparing(entry -> entry.title().tmdbId()))
                .map(entry -> entry.title().candidate()).toList();
        int start = Math.min((page - 1) * PAGE_SIZE, filtered.size());
        int end = Math.min(start + PAGE_SIZE, filtered.size());
        return new DiscoverPage(filtered.subList(start, end), page,
                (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE, filtered.size());
    }

    @Override
    public TmdbTitle getDetails(ContentType contentType, long tmdbId) {
        ContentType type = java.util.Objects.requireNonNull(contentType, "contentType");
        TitleSourceSupport.checkId(tmdbId);
        return catalog.stream().map(DemoTitle::title)
                .filter(title -> title.mediaType() == type && title.tmdbId() == tmdbId)
                .findFirst().orElseThrow(ExternalServiceException::new);
    }

    private DemoTitle create(ContentType type, int index) {
        boolean movie = type == ContentType.MOVIES;
        TitleGenre mainGenre = switch (index % 3) {
            case 0 -> new TitleGenre(movie ? 28 : 10759, movie ? "Action" : "Action & Adventure");
            case 1 -> new TitleGenre(35, "Comedy");
            default -> new TitleGenre(18, "Drama");
        };
        List<TitleGenre> genres = mainGenre.id() == 18 ? List.of(mainGenre)
                : List.of(mainGenre, new TitleGenre(18, "Drama"));
        String name = NAMES.get(index) + (movie ? "" : ": The Series");
        TmdbTitle title = new TmdbTitle((movie ? 9000001L : 9100001L) + index, type,
                name, 2000 + index,
                "In " + name + ", an unexpected discovery brings three strangers together. "
                        + "Their search for a missing message leads through a coastal town, "
                        + "an abandoned observatory and a choice that will change their lives. "
                        + "An original fictional story created for the Movie Match offline demo.",
                movie ? "/demo/poster-movie.svg" : "/demo/poster-tv.svg",
                BigDecimal.valueOf(1000 - index * 10L),
                BigDecimal.valueOf(70 + index % 20L, 1),
                movie ? 85 + (index % 8) * 8 : 24 + (index % 6) * 6,
                genres, "Nora Vale", List.of("Elliot Reed", "Maya West"),
                List.of("Alex Rowan", "Sam Rivers", "Robin Lane"),
                movie ? "PG-13" : "TV-14", 20000000L + index * 1000000L,
                80000000L + index * 2000000L, "en");
        return new DemoTitle(title, 200 + index * 100);
    }

    private record DemoTitle(TmdbTitle title, int voteCount) {
    }
}
