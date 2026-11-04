package com.team262.moviematch.service.game;

import com.team262.moviematch.dto.room.response.StartGameResponse;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.service.deck.DeckCandidateSelector;
import com.team262.moviematch.service.deck.TitleStoreService;
import com.team262.moviematch.source.DiscoverFilters;
import com.team262.moviematch.source.TmdbTitle;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameStartService {


    // It filters out sleazy headlines from the deeper reaches of Discover.

    static final int MIN_VOTE_COUNT = 100;

    private final GameStartTxService tx;
    private final DeckCandidateSelector selector;
    private final TitleStoreService titleStore;

    public StartGameResponse start(String code, Participant participant) {
        StartPlan plan = tx.claimStart(code, participant);                // short transaction: BUILDING
        try {
            DiscoverFilters filters = new DiscoverFilters(
                    plan.genreIds(), plan.maxDurationMinutes(), MIN_VOTE_COUNT);
            List<TmdbTitle> cards = selector.select(                 // TMDB, without transaction
                    plan.contentType(), filters, ThreadLocalRandom.current());
            List<Long> titleIds = titleStore.storeAll(cards);        // titles
            return tx.completeStart(plan.roomId(), titleIds);        // short transaction: VOTING
        } catch (RuntimeException e) {
            markFailedQuietly(plan.roomId());
            throw e;                                      // the original error goes to the client
        }
    }

    private void markFailedQuietly(Long roomId) {
        try {
            tx.markBuildFailed(roomId);
        } catch (RuntimeException e) {
            log.error("Could not mark deck build as FAILED for room {}", roomId, e);
        }
    }
}