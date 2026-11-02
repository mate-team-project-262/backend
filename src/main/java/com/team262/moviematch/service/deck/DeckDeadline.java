package com.team262.moviematch.service.deck;

import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.DeckBuildException;
import java.time.Duration;
import java.util.function.LongSupplier;

public final class DeckDeadline {
    private final LongSupplier nanoTime;
    private final long started;
    private final long timeout;

    public DeckDeadline(Duration timeout) {
        this(timeout, System::nanoTime);
    }

    DeckDeadline(Duration timeout, LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        this.started = nanoTime.getAsLong();
        this.timeout = timeout.toNanos();
    }

    public long remainingNanos() {
        long remaining = timeout - (nanoTime.getAsLong() - started);
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) {
            throw new DeckBuildException(ErrorCode.DECK_BUILD_TIMEOUT, "Deck preparation timed out");
        }
        return remaining;
    }

    public void check() {
        remainingNanos();
    }
}
