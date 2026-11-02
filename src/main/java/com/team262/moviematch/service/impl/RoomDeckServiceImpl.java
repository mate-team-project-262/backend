package com.team262.moviematch.service.impl;

import com.team262.moviematch.config.deck.DeckProperties;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ConflictException;
import com.team262.moviematch.service.RoomDeckService;
import com.team262.moviematch.service.deck.DeckCandidateSelector;
import com.team262.moviematch.service.deck.DeckDeadline;
import com.team262.moviematch.service.deck.RoomDeckStore;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Service;

@Service
public class RoomDeckServiceImpl implements RoomDeckService {
    private final RoomDeckStore store;
    private final DeckCandidateSelector selector;
    private final DeckProperties properties;

    public RoomDeckServiceImpl(RoomDeckStore store, DeckCandidateSelector selector,
                               DeckProperties properties) {
        this.store = store;
        this.selector = selector;
        this.properties = properties;
    }

    @Override
    public List<Long> prepare(String code, long participantId) {
        DeckDeadline deadline = new DeckDeadline(properties.buildTimeout());
        RoomDeckStore.Claim claim = store.claim(code, participantId,
                Duration.ofNanos(deadline.remainingNanos()));
        if (!claim.titleIds().isEmpty()) {
            return claim.titleIds();
        }
        try {
            var cards = selector.select(claim.contentType(), claim.filters(), deadline, new Random());
            return store.complete(claim, cards, deadline);
        } catch (RuntimeException failure) {
            boolean interrupted = Thread.interrupted();
            try {
                store.fail(claim);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure); // Lease expiry still permits recovery.
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            throw failure;
        }
    }

    @Override
    public List<Long> getTitleIds(String code, long participantId) {
        List<Long> ids = store.inspect(code, participantId, false).titleIds();
        if (ids.isEmpty()) {
            throw new ConflictException(ErrorCode.RESULT_NOT_READY, "Room deck is not ready");
        }
        return ids;
    }
}
