package com.team262.moviematch.service.deck;

import com.team262.moviematch.config.deck.DeckProperties;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.exception.ex.DeckBuildException;
import com.team262.moviematch.exception.ex.ExternalServiceException;
import com.team262.moviematch.model.ContentType;
import com.team262.moviematch.source.DiscoverFilters;
import com.team262.moviematch.source.DiscoverPage;
import com.team262.moviematch.source.TitleCandidate;
import com.team262.moviematch.source.TitleGenre;
import com.team262.moviematch.source.TitleSource;
import com.team262.moviematch.source.TmdbTitle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class DeckCandidateSelector {
    private final TitleSource source;
    private final DeckProperties properties;

    public DeckCandidateSelector(TitleSource source, DeckProperties properties) {
        this.source = source;
        this.properties = properties;
    }

    public List<TmdbTitle> select(ContentType type, DiscoverFilters filters,
                                  DeckDeadline deadline, Random random) {
        DiscoverPage first = call(deadline, () -> source.discover(type, filters, 1));
        if (first.totalResults() < DeckProperties.SIZE) {
            throw new ConflictException(ErrorCode.NOT_ENOUGH_MOVIES, "Fewer than 20 candidates match the filters");
        }
        int pageCount = Math.min(properties.maxPageWindow(), Math.min(first.totalPages(), 500));
        if (pageCount == 0) {
            throw new ExternalServiceException();
        }
        List<Integer> pages = new ArrayList<>(IntStream.rangeClosed(1, pageCount).boxed().toList());
        Collections.shuffle(pages, random);
        int initialPages = Math.min(properties.initialPageCount(), pageCount);
        int discoverRequests = 1;
        Set<Long> seen = new HashSet<>(); // At most 20 IDs per discover request, never entity graphs.
        Deque<Long> candidates = new ArrayDeque<>();
        Deque<Long> reserve = new ArrayDeque<>();
        ContentType mediaType = type;

        for (int i = 0; i < initialPages; i++) {
            int pageNumber = pages.get(i);
            if (pageNumber != 1 && ++discoverRequests > properties.maxDiscoverRequests()) {
                throw selectionLimit();
            }
            DiscoverPage page = pageNumber == 1 ? first
                    : call(deadline, () -> source.discover(type, filters, pageNumber));
            List<Long> ids = shuffledIds(page, mediaType, seen, random);
            int quota = DeckProperties.SIZE / initialPages + (i < DeckProperties.SIZE % initialPages ? 1 : 0);
            for (int j = 0; j < ids.size(); j++) {
                if (j < quota) {
                    candidates.add(ids.get(j));
                } else if (reserve.size() < properties.maxDetailsRequests() - DeckProperties.SIZE) {
                    reserve.add(ids.get(j));
                }
            }
        }
        candidates.addAll(reserve); // At most maxDetailsRequests IDs.
        first = null;
        List<TmdbTitle> accepted = new ArrayList<>(DeckProperties.SIZE);
        int detailsRequests = 0;
        int nextPage = initialPages;

        while (accepted.size() < DeckProperties.SIZE) {
            deadline.check();
            if (detailsRequests >= properties.maxDetailsRequests()) {
                throw selectionLimit();
            }
            if (candidates.isEmpty()) {
                if (nextPage >= pages.size() || discoverRequests >= properties.maxDiscoverRequests()) {
                    throw selectionLimit();
                }
                int pageNumber = pages.get(nextPage++);
                discoverRequests++;
                DiscoverPage page = call(deadline, () -> source.discover(type, filters, pageNumber));
                int capacity = properties.maxDetailsRequests() - detailsRequests;
                shuffledIds(page, mediaType, seen, random).stream().limit(capacity).forEach(candidates::add);
                continue;
            }
            long id = candidates.removeFirst();
            detailsRequests++;
            TmdbTitle title = call(deadline, () -> source.getDetails(type, id));
            if (title == null || !Long.valueOf(id).equals(title.tmdbId()) || title.mediaType() != mediaType) {
                throw new ExternalServiceException();
            }
            if (eligible(title, filters)) {
                accepted.add(title);
            }
        }
        deadline.check();
        Collections.shuffle(accepted, random);
        return List.copyOf(accepted);
    }

    private List<Long> shuffledIds(DiscoverPage page, ContentType mediaType, Set<Long> seen, Random random) {
        List<Long> ids = new ArrayList<>();
        for (TitleCandidate candidate : page.items()) {
            if (candidate.tmdbId() == null || candidate.tmdbId() <= 0 || candidate.mediaType() != mediaType) {
                throw new ExternalServiceException();
            }
            if (seen.add(candidate.tmdbId())) {
                ids.add(candidate.tmdbId());
            }
        }
        Collections.shuffle(ids, random);
        return ids;
    }

    private <T> T call(DeckDeadline deadline, java.util.function.Supplier<T> request) {
        deadline.check();
        T result = request.get();
        deadline.check();
        return result;
    }

    private boolean eligible(TmdbTitle title, DiscoverFilters filters) {
        return title.title() != null && !title.title().isBlank()
                && (filters.maxDurationMinutes() == null
                    || title.runtimeMinutes() != null && title.runtimeMinutes() > 0
                    && title.runtimeMinutes() <= filters.maxDurationMinutes())
                && title.genres().stream().map(TitleGenre::id).toList().containsAll(filters.genreIds());
    }

    private DeckBuildException selectionLimit() {
        return new DeckBuildException(ErrorCode.DECK_SELECTION_LIMIT,
                "Could not fill the deck within the selection budget");
    }
}
